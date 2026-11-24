import { BaseErrorListener, CharStream, CommonTokenStream, type Token } from "antlr4ng";
import { expect, it } from "vitest";
import { FlixLexer } from "../src/generated/FlixLexer.js";
import { FlixParser } from "../src/generated/FlixParser.js";

it("rejects a malformed numeric body and keeps the following declaration", () => {
    const source = "def broken(): Int32 = 1_\ndef good(): Int32 = 42";
    const tokens = new CommonTokenStream(new FlixLexer(CharStream.fromString(source)));
    tokens.fill();
    expect(tokens.getTokens().filter((token) => token.type === FlixLexer.ERR_NUMBER).map((token) => token.text)).toEqual(["1_"]);
    const parser = new FlixParser(tokens);
    const errors: [string | null, string][] = [];
    parser.removeErrorListeners();
    parser.addErrorListener(new class extends BaseErrorListener {
        override syntaxError(_recognizer: unknown, offending: Token | null, _line: number, _column: number, message: string) {
            errors.push([offending?.text ?? null, message]);
        }
    }());
    const declarations = parser.compilationUnit().declaration();
    expect(parser.numberOfSyntaxErrors).toBe(1);
    expect(errors).toEqual([["1_", expect.stringContaining("Invalid numeric literal.")]]);
    expect(declarations.map((decl) => decl.defDeclaration()?.definitionName().getText())).toEqual(["broken", "good"]);
});

it("keeps a valid local definition inside the outer declaration", () => {
    const source = "def outer(): Int32 = def local(): Int32 = 42; local()";
    const parser = new FlixParser(new CommonTokenStream(new FlixLexer(CharStream.fromString(source))));
    parser.removeErrorListeners();
    const declarations = parser.compilationUnit().declaration();
    expect(parser.numberOfSyntaxErrors).toBe(0);
    expect(declarations.map((decl) => decl.defDeclaration()?.definitionName().getText())).toEqual(["outer"]);
});
