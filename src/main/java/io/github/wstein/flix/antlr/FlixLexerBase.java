package io.github.wstein.flix.antlr;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

/**
 * Lexer helpers for the parts of Flix tokenization that a context-free lexer rule cannot express.
 *
 * <p>Three mechanisms live here, each mirroring a specific piece of the reference lexer
 * ({@code phase/Lexer.scala} in the Flix compiler):
 *
 * <ul>
 *   <li><b>Whitespace-sensitive tokens.</b> {@code ->} and {@code .} each mean different things
 *       depending on the characters immediately around them. The grammar matches them
 *       unconditionally and calls back here to assign the token type, which costs nothing at
 *       prediction time — unlike a semantic predicate, which would defeat the lexer's DFA cache.
 *   <li><b>Operator reclassification.</b> The reference scans a maximal run of operator characters
 *       and only then checks whether the <i>whole run</i> is a reserved spelling. That is not
 *       longest-prefix match: {@code <--} is one generic operator, not {@code <-} followed by
 *       {@code -}.
 *   <li><b>String interpolation nesting.</b> Interpolations nest without bound, and each level
 *       tracks its own brace depth so that a bare block inside an interpolation does not close it.
 * </ul>
 */
public abstract class FlixLexerBase extends Lexer {

    /**
     * Reserved operator spellings composed only of user-operator characters.
     *
     * <p>The colon family ({@code :}, {@code :-}, {@code ::}, {@code :::}) is deliberately absent:
     * {@code :} is not a user-operator character, so those cannot occur inside an operator run and
     * are ordinary lexer rules instead.
     */
    private static final Map<String, Integer> RESERVED_OPERATORS = new HashMap<>();

    static {
        RESERVED_OPERATORS.put("!", FlixLexer.BANG);
        RESERVED_OPERATORS.put("!=", FlixLexer.BANG_EQUAL);
        RESERVED_OPERATORS.put("&", FlixLexer.AMPERSAND);
        RESERVED_OPERATORS.put("*", FlixLexer.STAR);
        RESERVED_OPERATORS.put("+", FlixLexer.PLUS);
        RESERVED_OPERATORS.put("-", FlixLexer.MINUS);
        RESERVED_OPERATORS.put("<", FlixLexer.ANGLE_L);
        RESERVED_OPERATORS.put("<+>", FlixLexer.ANGLED_PLUS);
        RESERVED_OPERATORS.put("<-", FlixLexer.ARROW_THIN_L);
        RESERVED_OPERATORS.put("<=", FlixLexer.ANGLE_L_EQUAL);
        RESERVED_OPERATORS.put("<=>", FlixLexer.ANGLED_EQUAL);
        RESERVED_OPERATORS.put("=", FlixLexer.EQUAL);
        RESERVED_OPERATORS.put("==", FlixLexer.EQUAL_EQUAL);
        RESERVED_OPERATORS.put("=>", FlixLexer.ARROW_THICK_R);
        RESERVED_OPERATORS.put(">", FlixLexer.ANGLE_R);
        RESERVED_OPERATORS.put(">=", FlixLexer.ANGLE_R_EQUAL);
        RESERVED_OPERATORS.put("^", FlixLexer.CARET);
        RESERVED_OPERATORS.put("|", FlixLexer.BAR);
    }

    /**
     * Brace depth per open string interpolation, innermost last.
     *
     * <p>An interpolation ends at the {@code }} that returns its own depth to zero; braces belonging
     * to a block inside the interpolation must not close it. Nesting is unbounded in the reference,
     * so this is a stack rather than a counter.
     */
    private final Deque<Integer> interpolationBraceDepth = new ArrayDeque<>();

    protected FlixLexerBase(CharStream input) {
        super(input);
    }

    // ---------------------------------------------------------------------
    // Whitespace-sensitive tokens
    // ---------------------------------------------------------------------

    /**
     * Assigns the token type for {@code ->}.
     *
     * <p>Whitespace on either side makes it the function or type arrow; no whitespace on either side
     * makes it struct field access. Out-of-bounds counts as whitespace on both sides, matching the
     * reference.
     */
    protected void classifyArrow() {
        setType(isWhitespaceBefore() || isWhitespaceAfter() ? FlixLexer.ARROW_WS : FlixLexer.ARROW_TIGHT);
    }

    /**
     * Assigns the token type for {@code .}, which has three outcomes.
     *
     * <p>Trailing whitespace makes it the Datalog constraint terminator. Leading whitespace is an
     * error in the reference; it is given a distinct type here so that no parser rule accepts it and
     * the diagnostic lands on the offending character.
     */
    protected void classifyDot() {
        if (isWhitespaceBefore()) {
            setType(FlixLexer.FREE_DOT);
        } else if (isWhitespaceAfter()) {
            setType(FlixLexer.DOT_WS);
        } else {
            setType(FlixLexer.DOT);
        }
    }

    /** Returns whether the character immediately preceding this token is whitespace or start-of-input. */
    private boolean isWhitespaceBefore() {
        int index = _tokenStartCharIndex - 1;
        if (index < 0) {
            return true;
        }
        return isFlixWhitespace(charAt(index));
    }

    /** Returns whether the character immediately following this token is whitespace or end-of-input. */
    private boolean isWhitespaceAfter() {
        int next = _input.LA(1);
        return next == CharStream.EOF || isFlixWhitespace(next);
    }

    private int charAt(int index) {
        int marker = _input.mark();
        int saved = _input.index();
        try {
            _input.seek(index);
            return _input.LA(1);
        } finally {
            _input.seek(saved);
            _input.release(marker);
        }
    }

    /**
     * Mirrors {@code Character.isWhitespace}, which is what the reference lexer uses.
     *
     * <p>Spelled out rather than delegated so that the classification cannot drift with the platform
     * locale. Notably this excludes the non-breaking spaces U+00A0, U+2007 and U+202F.
     */
    private static boolean isFlixWhitespace(int c) {
        return c == ' '
                || (c >= '\t' && c <= '\r')
                || (c >= 0x1C && c <= 0x1F)
                || c == 0x1680
                || (c >= 0x2000 && c <= 0x2006)
                || (c >= 0x2008 && c <= 0x200A)
                || c == 0x2028
                || c == 0x2029
                || c == 0x205F
                || c == 0x3000;
    }

    // ---------------------------------------------------------------------
    // Operator reclassification
    // ---------------------------------------------------------------------

    /**
     * Reclassifies a maximal run of operator characters.
     *
     * <p>A run that is exactly a reserved spelling becomes that token; anything else is a
     * user-defined operator. A run carrying the {@code _} prefix is always user-defined, because the
     * reference reaches it through a path that never consults the reserved table.
     */
    protected void classifyOperator() {
        String text = getText();
        if (text.startsWith("_")) {
            setType(FlixLexer.GENERIC_OPERATOR);
            return;
        }
        if ("->".equals(text)) {
            classifyArrow();
            return;
        }
        Integer reserved = RESERVED_OPERATORS.get(text);
        setType(reserved != null ? reserved : FlixLexer.GENERIC_OPERATOR);
    }

    /**
     * Guards the keywords whose spelling ends in a non-name character: {@code Array#}, {@code List#},
     * {@code Map#}, {@code Set#}, {@code Vector#} and {@code choose*}.
     *
     * <p>The reference only accepts a keyword when the following character cannot continue a name, so
     * {@code Array#x} must fall back to a name, a hash and a name.
     */
    protected boolean notNameChar() {
        int c = _input.LA(1);
        if (c == CharStream.EOF) {
            return true;
        }
        boolean nameChar = (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_'
                || c == '!'
                || c == '$';
        return !nameChar;
    }


    // ---------------------------------------------------------------------
    // String interpolation
    // ---------------------------------------------------------------------

    /** Records that an interpolation has opened, at brace depth zero. */
    protected void openInterpolation() {
        interpolationBraceDepth.push(0);
    }

    /** Tracks a {@code &#123;} that belongs to a block inside an interpolation. */
    protected void enterBrace() {
        if (!interpolationBraceDepth.isEmpty()) {
            interpolationBraceDepth.push(interpolationBraceDepth.pop() + 1);
        }
    }

    /**
     * Handles a {@code &#125;} inside an interpolation.
     *
     * @return {@code true} when this brace closes the innermost interpolation, in which case the
     *     grammar emits the interpolation terminator and pops back into the string.
     */
    protected boolean exitBrace() {
        if (interpolationBraceDepth.isEmpty()) {
            return false;
        }
        int depth = interpolationBraceDepth.pop();
        if (depth == 0) {
            return true;
        }
        interpolationBraceDepth.push(depth - 1);
        return false;
    }

    // ---------------------------------------------------------------------
    // Miscellaneous
    // ---------------------------------------------------------------------

    /**
     * Drops the {@code $} that escapes a keyword used as a name.
     *
     * <p>The reference excludes the {@code $} from the token span entirely, leaving a one-character
     * hole in token coverage. Here the span stays contiguous and only the text is adjusted, so that
     * offsets remain usable for tooling while the name still resolves correctly.
     */
    protected void stripEscape() {
        setText(getText().substring(1));
    }

    /**
     * Returns whether the next character in the input stream is a Flix name character.
     *
     * <p>Used as a predicate tail guard for keywords so that e.g. {@code let!} is lexed as a single
     * name rather than the {@code let} keyword followed by an exclamation mark.
     */
    protected boolean isNameCharFollow() {
        int c = _input.LA(1);
        if (c == CharStream.EOF) {
            return false;
        }
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_'
                || c == '!'
                || c == '$';
    }

    @Override
    public Token emitEOF() {
        // An unterminated string or interpolation must not leave the lexer in a pushed mode; a
        // reused lexer instance would otherwise start mid-string.
        interpolationBraceDepth.clear();
        _mode = Lexer.DEFAULT_MODE;
        _modeStack.clear();
        return super.emitEOF();
    }
}
