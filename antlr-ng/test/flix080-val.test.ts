import { describe, expect, it } from "vitest";
import { CharStream, CommonTokenStream } from "antlr4ng";
import { FlixLexer } from "../src/generated/FlixLexer.js";
import { FlixParser } from "../src/generated/FlixParser.js";

describe("Flix 0.80 val syntax", () => {
    it("reserves val without splitting longer names", () => {
        const lexer = new FlixLexer(CharStream.fromString("val value val!"));
        const tokens = lexer.getAllTokens().filter(t => t.channel === 0);
        expect(tokens.map(t => t.text)).toEqual(["val", "value", "val!"]);
        expect(lexer.vocabulary.getSymbolicName(tokens[0].type)).toBe("VAL");
        expect(tokens[0].type).not.toBe(tokens[1].type);
        expect(tokens[1].type).toBe(tokens[2].type);
    });
    for (const source of ["mod M { pub val field: Int32 = 42 }", "def f(): Int32 = let x = 42; x"]) {
        it(`parses ${source}`, () => {
            const parser = new FlixParser(new CommonTokenStream(new FlixLexer(CharStream.fromString(source))));
            parser.removeErrorListeners();
            parser.compilationUnit();
            expect(parser.numberOfSyntaxErrors).toBe(0);
        });
    }
    it("requires an equals sign and initializer", () => {
        for (const source of ["val field: Int32", "val field: Int32 42", "val field: Int32 ="]) {
            const parser = new FlixParser(new CommonTokenStream(new FlixLexer(CharStream.fromString(source))));
            parser.removeErrorListeners();
            parser.compilationUnit();
            expect(parser.numberOfSyntaxErrors, source).toBeGreaterThan(0);
        }
    });
    it("rejects val as an unescaped function name", () => {
        const parser = new FlixParser(new CommonTokenStream(new FlixLexer(CharStream.fromString("def val(): Int32 = 42"))));
        parser.removeErrorListeners();
        parser.compilationUnit();
        expect(parser.numberOfSyntaxErrors).toBeGreaterThan(0);
    });
    it("rejects val as an unescaped parameter", () => {
        const parser = new FlixParser(new CommonTokenStream(new FlixLexer(CharStream.fromString("def f(val: Int32): Int32 = val"))));
        parser.removeErrorListeners();
        parser.compilationUnit();
        expect(parser.numberOfSyntaxErrors).toBeGreaterThan(0);
    });
});
