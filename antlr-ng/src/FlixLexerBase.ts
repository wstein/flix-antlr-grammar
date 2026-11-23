import { CharStream, Lexer } from "antlr4ng";

/**
 * Lexer helpers for Flix tokenization in antlr-ng (TypeScript runtime).
 *
 * Mirrors the logic in FlixLexerBase.java for the antlr-ng structural target.
 */
export abstract class FlixLexerBase extends Lexer {
    // Reserved spellings composed only of user-operator characters. `/`, `~` and the
    // colon family are absent by design: they cannot occur inside an operator run.
    static readonly RESERVED_OPERATORS = new Map<string, string>([
        ["!", "BANG"],
        ["!=", "BANG_EQUAL"],
        ["&", "AMPERSAND"],
        ["*", "STAR"],
        ["+", "PLUS"],
        ["-", "MINUS"],
        ["<", "ANGLE_L"],
        ["<+>", "ANGLED_PLUS"],
        ["<-", "ARROW_THIN_L"],
        ["<=", "ANGLE_L_EQUAL"],
        ["<=>", "ANGLED_EQUAL"],
        ["=", "EQUAL"],
        ["==", "EQUAL_EQUAL"],
        ["=>", "ARROW_THICK_R"],
        [">", "ANGLE_R"],
        [">=", "ANGLE_R_EQUAL"],
        ["^", "CARET"],
        ["|", "BAR"],
    ]);

    private interpolationBraceDepth: number[] = [];

    public constructor(input: CharStream) {
        super(input);
    }

    protected classifyColonColon(): void {
        this.type = this.isWhitespaceBefore() || this.isWhitespaceAfter()
            ? (this.constructor as any).COLON_COLON : (this.constructor as any).COLON_COLON_TIGHT;
    }

    protected classifyArrow(): void {
        const arrowType = (this.isWhitespaceBefore() || this.isWhitespaceAfter())
            ? (this.constructor as any).ARROW_WS
            : (this.constructor as any).ARROW_TIGHT;
        this.type = arrowType;
    }

    protected classifyDot(): void {
        if (this.isWhitespaceBefore()) {
            this.type = (this.constructor as any).FREE_DOT;
        } else if (this.isWhitespaceAfter()) {
            this.type = (this.constructor as any).DOT_WS;
        } else {
            this.type = (this.constructor as any).DOT;
        }
    }

    protected classifyOperator(): void {
        const text = this.text;
        if (text.startsWith("_")) {
            this.type = (this.constructor as any).GENERIC_OPERATOR;
            return;
        }
        if (text === "->") {
            this.classifyArrow();
            return;
        }
        const symbolicName = FlixLexerBase.RESERVED_OPERATORS.get(text);
        if (symbolicName) {
            const tokenType = (this.constructor as any)[symbolicName];
            this.type = tokenType !== undefined ? tokenType : (this.constructor as any).GENERIC_OPERATOR;
        } else {
            this.type = (this.constructor as any).GENERIC_OPERATOR;
        }
    }

    /** Finishes a block comment after `/*`; unterminated → UNTERMINATED_BLOCK_COMMENT. */
    protected acceptBlockComment(): void {
        const L = this.constructor as any;
        let level = 1;
        while (this.inputStream.LA(1) !== -1) {
            if (this.inputStream.LA(1) === 0x2f && this.inputStream.LA(2) === 0x2a) {
                this.advance();
                this.advance();
                level++;
                continue;
            }
            if (this.inputStream.LA(1) === 0x2a && this.inputStream.LA(2) === 0x2f) {
                this.advance();
                this.advance();
                level--;
                if (level === 0) {
                    this.type = L.BLOCK_COMMENT;
                    // antlr-ng does not emit static COMMENTS/DOC_COMMENTS channel
                    // constants (unlike the Java target); resolve by name.
                    const comments = (L.channelNames as string[] | undefined)?.indexOf("COMMENTS") ?? -1;
                    this.channel = comments >= 0 ? comments : 2;
                    return;
                }
                continue;
            }
            this.advance();
        }
        this.type = L.UNTERMINATED_BLOCK_COMMENT;
    }

    /** Finishes a number after the first digit; trailing junk → ERR_NUMBER. */
    protected acceptNumber(): void {
        const L = this.constructor as any;
        let mustBeFloat = false;
        try {
            this.acceptDigits(true);
            if (this.matchChar(".")) {
                mustBeFloat = true;
                this.acceptDigits(false);
            }
            if (this.matchChar("e")) {
                mustBeFloat = true;
                if (!this.matchChar("+")) {
                    this.matchChar("-");
                }
                this.acceptDigits(false);
                if (this.matchChar(".")) {
                    this.acceptDigits(false);
                }
            }
        } catch {
            this.type = L.ERR_NUMBER;
            return;
        }

        const c = this.inputStream.LA(1);
        if (c === "i".charCodeAt(0)) {
            if (this.matchSuffix("i8") || this.matchSuffix("i16") || this.matchSuffix("i32")
                || this.matchSuffix("i64") || this.matchSuffix("ii")) {
                this.type = this.finishNumber(mustBeFloat, true);
            } else {
                this.consumeNumberLike();
                this.type = L.ERR_NUMBER;
            }
        } else if (c === "f".charCodeAt(0)) {
            // Float suffixes force FLOAT_LITERAL even without '.' / exponent (`42f32`).
            if (this.matchSuffix("f32") || this.matchSuffix("f64") || this.matchSuffix("ff")) {
                this.type = this.finishNumber(true, false);
            } else {
                this.consumeNumberLike();
                this.type = L.ERR_NUMBER;
            }
        } else if (this.isNumberLike(c)) {
            this.consumeNumberLike();
            this.type = L.ERR_NUMBER;
        } else {
            this.type = mustBeFloat ? L.FLOAT_LITERAL : L.INT_LITERAL;
        }
    }

    /** Finishes a hex literal after `0x`. */
    protected acceptHexNumber(): void {
        const L = this.constructor as any;
        if (this.consumeWhile((ch) => this.isHexDigit(ch)) === 0) {
            this.consumeNumberLike();
            this.type = L.ERR_NUMBER;
            return;
        }
        while (this.matchChar("_")) {
            if (this.consumeWhile((ch) => this.isHexDigit(ch)) === 0) {
                this.consumeNumberLike();
                this.type = L.ERR_NUMBER;
                return;
            }
        }
        const c = this.inputStream.LA(1);
        if (c === "i".charCodeAt(0)) {
            if (this.matchSuffix("i8") || this.matchSuffix("i16") || this.matchSuffix("i32")
                || this.matchSuffix("i64") || this.matchSuffix("ii")) {
                if (this.isNumberLike(this.inputStream.LA(1))) {
                    this.consumeNumberLike();
                    this.type = L.ERR_NUMBER;
                } else {
                    this.type = L.HEX_LITERAL;
                }
            } else {
                this.consumeNumberLike();
                this.type = L.ERR_NUMBER;
            }
        } else if (this.isNumberLike(c)) {
            this.consumeNumberLike();
            this.type = L.ERR_NUMBER;
        } else {
            this.type = L.HEX_LITERAL;
        }
    }

    private finishNumber(mustBeFloat: boolean, intSuffix: boolean): number {
        const L = this.constructor as any;
        if (this.isNumberLike(this.inputStream.LA(1))) {
            this.consumeNumberLike();
            return L.ERR_NUMBER;
        }
        if (mustBeFloat && intSuffix) {
            return L.ERR_NUMBER;
        }
        return mustBeFloat ? L.FLOAT_LITERAL : L.INT_LITERAL;
    }

    private acceptDigits(soft: boolean): void {
        if (this.consumeWhile((ch) => this.isDigit(ch)) === 0 && !soft) {
            this.consumeNumberLike();
            throw new Error("number-lex");
        }
        while (this.matchChar("_")) {
            if (this.consumeWhile((ch) => this.isDigit(ch)) === 0) {
                this.consumeNumberLike();
                throw new Error("number-lex");
            }
        }
    }

    private matchSuffix(suffix: string): boolean {
        for (let i = 0; i < suffix.length; i++) {
            if (this.inputStream.LA(i + 1) !== suffix.charCodeAt(i)) {
                return false;
            }
        }
        for (let i = 0; i < suffix.length; i++) {
            this.advance();
        }
        return true;
    }

    private matchChar(ch: string): boolean {
        if (this.inputStream.LA(1) !== ch.charCodeAt(0)) {
            return false;
        }
        this.advance();
        return true;
    }

    private consumeWhile(pred: (c: number) => boolean): number {
        let n = 0;
        while (pred(this.inputStream.LA(1))) {
            this.advance();
            n++;
        }
        return n;
    }

    private consumeNumberLike(): void {
        while (this.isNumberLike(this.inputStream.LA(1))) {
            this.advance();
        }
    }

    /**
     * Consume one character through the ATN simulator so line/column stay in sync.
     * `inputStream.consume()` alone advances the stream but leaves the position counters
     * behind, which shifts every later token's diagnostics.
     */
    private advance(): void {
        (this.interpreter as { consume(input: CharStream): void }).consume(this.inputStream);
    }

    private isDigit(c: number): boolean {
        return c >= 0x30 && c <= 0x39;
    }

    private isHexDigit(c: number): boolean {
        return this.isDigit(c)
            || (c >= 0x61 && c <= 0x66)
            || (c >= 0x41 && c <= 0x46);
    }

    private isLetter(c: number): boolean {
        return (c >= 0x61 && c <= 0x7a) || (c >= 0x41 && c <= 0x5a);
    }

    private isNumberLike(c: number): boolean {
        return this.isDigit(c) || this.isLetter(c) || c === 0x2e || c === 0x5f;
    }

    protected openInterpolation(): void {
        this.interpolationBraceDepth.push(0);
    }

    protected enterBrace(): void {
        if (this.interpolationBraceDepth.length > 0) {
            const current = this.interpolationBraceDepth.pop()!;
            this.interpolationBraceDepth.push(current + 1);
        }
    }

    protected exitBrace(): boolean {
        if (this.interpolationBraceDepth.length === 0) {
            return false;
        }
        const depth = this.interpolationBraceDepth.pop()!;
        if (depth === 0) {
            return true;
        }
        this.interpolationBraceDepth.push(depth - 1);
        return false;
    }

    protected stripEscape(): void {
        this.text = this.text.substring(1);
    }

    protected isNameCharFollow(): boolean {
        const c = this.inputStream.LA(1);
        if (c === -1) {
            return false;
        }
        const ch = String.fromCharCode(c);
        return /[a-zA-Z0-9_!$]/.test(ch);
    }

    private isWhitespaceBefore(): boolean {
        const index = this.tokenStartCharIndex - 1;
        if (index < 0) {
            return true;
        }
        return this.isFlixWhitespace(this.charAt(index));
    }

    private isWhitespaceAfter(): boolean {
        const next = this.inputStream.LA(1);
        return next === -1 || this.isFlixWhitespace(next);
    }

    private charAt(index: number): number {
        const marker = this.inputStream.mark();
        const saved = this.inputStream.index;
        try {
            this.inputStream.seek(index);
            return this.inputStream.LA(1);
        } finally {
            this.inputStream.seek(saved);
            this.inputStream.release(marker);
        }
    }

    private isFlixWhitespace(c: number): boolean {
        return c === 32 || (c >= 9 && c <= 13) || (c >= 0x1C && c <= 0x1F) || c === 0x1680 ||
            (c >= 0x2000 && c <= 0x2006) || (c >= 0x2008 && c <= 0x200A) || c === 0x2028 ||
            c === 0x2029 || c === 0x205F || c === 0x3000;
    }

    public override emitEOF(): any {
        this.interpolationBraceDepth = [];
        this.mode = Lexer.DEFAULT_MODE;
        this.modeStack.length = 0;
        return super.emitEOF();
    }
}
