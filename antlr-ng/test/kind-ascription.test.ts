import { describe, expect, it } from "vitest";
import { CharStream, CommonTokenStream } from "antlr4ng";
import { FlixLexer } from "../src/generated/FlixLexer.js";
import { FlixParser } from "../src/generated/FlixParser.js";

function syntaxErrors(source: string): number {
    const parser = new FlixParser(new CommonTokenStream(new FlixLexer(CharStream.fromString(source))));
    parser.removeErrorListeners();
    parser.compilationUnit();
    return parser.numberOfSyntaxErrors;
}

describe("type kind ascription", () => {
    it("rejects a second ascription at the same level", () => {
        expect(syntaxErrors("pub type alias Twice = Int32 : Type : Type")).toBeGreaterThan(0);
    });

    it("accepts one ascription and a separate one inside parentheses", () => {
        expect(syntaxErrors("pub type alias One = List[Int32] : Type")).toBe(0);
        expect(syntaxErrors("pub type alias Nested = (Int32 : Type) : Type")).toBe(0);
    });
});
