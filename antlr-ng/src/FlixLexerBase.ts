import { CharStream, Lexer } from "antlr4ng";

/**
 * Lexer helpers for Flix tokenization in antlr-ng (TypeScript runtime).
 *
 * Mirrors the logic in FlixLexerBase.java for the antlr-ng structural target.
 */
export abstract class FlixLexerBase extends Lexer {
    // Reserved spellings composed only of user-operator characters. `/`, `~` and the
    // colon family are absent by design: they cannot occur inside an operator run.
    private static readonly RESERVED_OPERATORS = new Map<string, string>([
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
