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

    it("keeps line/column in sync after numbers and block comments", () => {
        const plus = new FlixLexer(CharStream.fromString("10 + 20")).getAllTokens()
            .find((t) => t.type === FlixLexer.PLUS)!;
        expect(plus.column).toBe(3);

        const defTok = new FlixLexer(CharStream.fromString("/* a\n b */\ndef")).getAllTokens()
            .find((t) => t.type === FlixLexer.DEF)!;
        expect(defTok.line).toBe(3);
        expect(defTok.column).toBe(0);
    });

    it("tokenises bare float suffixes as FLOAT_LITERAL", () => {
        const types = new FlixLexer(CharStream.fromString("42f32 42f64 42ff")).getAllTokens()
            .filter((t) => t.channel === 0)
            .map((t) => t.type);
        expect(types).toEqual([
            FlixLexer.FLOAT_LITERAL,
            FlixLexer.FLOAT_LITERAL,
            FlixLexer.FLOAT_LITERAL,
        ]);
    });

    it("absorbs a trailing backslash into UNTERMINATED_REGEX", () => {
        const tokens = new FlixLexer(CharStream.fromString("regex\"tail\\")).getAllTokens()
            .filter((t) => t.channel === 0);
        expect(tokens.map((t) => t.type)).toEqual([FlixLexer.UNTERMINATED_REGEX]);
        expect(tokens[0].text).toBe("regex\"tail\\");
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
