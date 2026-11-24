import { CharStream, Token } from "antlr4ng";
import { describe, expect, it } from "vitest";
import { FlixLexer } from "../src/generated/FlixLexer.js";

const comments = FlixLexer.channelNames.indexOf("COMMENTS");
const docs = FlixLexer.channelNames.indexOf("DOC_COMMENTS");

describe("slash comment boundaries", () => {
    const cases = [
        ["//", FlixLexer.LINE_COMMENT, comments],
        ["// text", FlixLexer.LINE_COMMENT, comments],
        ["///", FlixLexer.DOC_COMMENT, docs],
        ["/// text", FlixLexer.DOC_COMMENT, docs],
        ["///x", FlixLexer.DOC_COMMENT, docs],
        ["////", FlixLexer.LINE_COMMENT, comments],
        ["//// banner", FlixLexer.LINE_COMMENT, comments],
        ["/////", FlixLexer.LINE_COMMENT, comments],
    ] as const;

    for (const newline of ["\n", "\r\n"]) {
        it.each(cases)("classifies %j before a declaration with " + JSON.stringify(newline), (source, kind, channel) => {
            const lexer = new FlixLexer(CharStream.fromString(`${source}${newline}def f(): Unit = ()`));
            const [comment, declaration] = lexer.getAllTokens().filter((token) => token.channel !== Token.HIDDEN_CHANNEL);
            // With CRLF, the grammar excludes '\r' from comment text. Flix 0.80.0 includes it (issue #50).
            expect([comment.text, comment.type, comment.channel]).toEqual([source, kind, channel]);
            expect([declaration.type, declaration.channel]).toEqual([FlixLexer.DEF, Token.DEFAULT_CHANNEL]);
        });
    }
});
