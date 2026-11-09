import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { CharStream, CommonTokenStream } from "antlr4ng";
import { FlixLexer } from "../src/generated/FlixLexer.js";
import { FlixParser } from "../src/generated/FlixParser.js";

describe("Flix 0.79 syntax", () => {
    for (const source of [
        "def law(lawful: Int32): Int32 = lawful",
        "use flixball::Game.Board",
            "def f(): Option[Int32] = forM (x <- Some(1); if x > 0; y = x + 1) yield y",
        "use flixball::{Game, Board}",
        "type alias S[r] = #{P(Int32) | r}",
        "type alias S[r] = #(P(Int32) | r)",
        "type alias S[r] = #|P(Int32) | r|#",
        "type alias S[r] = #{P(Int32) | Rows[r]}",
        "type alias S[r] = #(P(Int32) | Rows[r])",
        "type alias S[r] = #|P(Int32) | Rows[r]|#",
    ]) {
        it(source, () => {
            const parser = new FlixParser(new CommonTokenStream(new FlixLexer(CharStream.fromString(source))));
            parser.compilationUnit();
            expect(parser.numberOfSyntaxErrors).toBe(0);
        });
    }
    it("has exactly the pinned 82 keywords", () => {
        const actual = FlixLexer.literalNames.filter((s): s is string => Boolean(s))
            .map(s => s.slice(1, -1)).filter(s => /^[a-zA-Z][a-zA-Z0-9_#*]+$/.test(s));
        const expected = readFileSync(new URL("../../fixtures/keywords.txt", import.meta.url), "utf8")
            .trim().split(/\r?\n/);
        expect(actual.sort()).toEqual(expected.sort());
        expect(expected).toHaveLength(82);
    });

    it("classifies tight and whitespace-separated cons tokens", () => {
        for (const [source, type] of [["a::b", FlixLexer.COLON_COLON_TIGHT], ["a ::b", FlixLexer.COLON_COLON],
            ["a:: b", FlixLexer.COLON_COLON], ["::", FlixLexer.COLON_COLON]] as const) {
            const lexer = new FlixLexer(CharStream.fromString(source));
            const tokens = lexer.getAllTokens().filter(t => t.channel === 0);
            expect(tokens.find(t => t.text === "::")?.type).toBe(type);
        }
    });

    it("requires a package before a direct grouped use", () => {
        const parser = new FlixParser(new CommonTokenStream(new FlixLexer(CharStream.fromString("use {Game, Board}"))));
        parser.removeErrorListeners();
        parser.compilationUnit();
        expect(parser.numberOfSyntaxErrors).toBeGreaterThan(0);
    });

});
