import { describe, expect, it } from "vitest";
import { CharStream } from "antlr4ng";
import { FlixLexerBase } from "../src/FlixLexerBase.js";
import { FlixLexer } from "../src/generated/FlixLexer.js";

/**
 * Parity checks for the hand-written runtime support.
 *
 * These assert the invariants of FlixLexerBase that must match FlixLexerBase.java. See
 * corpus-coverage.test.ts for the gate that exercises the generated parser itself
 * (docs/DEFECTS.md D11).
 */
describe("FlixLexerBase", () => {
    it("places finished block comments on the COMMENTS channel", () => {
        const lexer = new FlixLexer(CharStream.fromString("/* block */ 1"));
        const tokens = lexer.getAllTokens().filter((t) => t.type !== -1);
        const block = tokens.find((t) => t.type === FlixLexer.BLOCK_COMMENT);
        expect(block).toBeDefined();
        expect(block!.channel).toBe(FlixLexer.channelNames.indexOf("COMMENTS"));
        expect(tokens.some((t) => t.type === FlixLexer.INT_LITERAL && t.channel === 0)).toBe(true);
    });

    it("declares only reserved spellings made of user-operator characters", () => {
        const table = (FlixLexerBase as unknown as {
            RESERVED_OPERATORS: Map<string, string>;
        }).RESERVED_OPERATORS;

        // `/`, `~` and the colon family are excluded from the user-operator character set, so
        // they can never appear in an operator run and must not be listed here.
        for (const spelling of table.keys()) {
            expect(spelling).toMatch(/^[+\-*<>=!&|^$]+$/);
        }
    });

    it("covers exactly the spellings the Java implementation reserves", () => {
        const table = (FlixLexerBase as unknown as {
            RESERVED_OPERATORS: Map<string, string>;
        }).RESERVED_OPERATORS;

        expect([...table.keys()].sort()).toEqual(
            ["!", "!=", "&", "*", "+", "-", "<", "<+>", "<-", "<=", "<=>", "=", "==", "=>", ">", ">=", "^", "|"].sort(),
        );
    });
});
