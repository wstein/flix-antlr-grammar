package io.github.wstein.flix.antlr

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.Token
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FlixLexerTest {
    private fun lex(input: String): List<Token> {
        val stream = CharStreams.fromString(input)
        val lexer = FlixLexer(stream)
        val tokens = mutableListOf<Token>()
        while (true) {
            val token = lexer.nextToken()
            if (token.type == Token.EOF) break
            tokens.add(token)
        }
        return tokens
    }

    private fun lexAllChannels(input: String): List<Token> = lex(input)

    private fun lexDefaultChannel(input: String): List<Token> =
        lex(input).filter { it.channel == Token.DEFAULT_CHANNEL }

    @Test
    fun testKeywords() {
        val tokens =
            lexDefaultChannel(
                "def let match pub enum alias case if else try catch for forall yield into mod mut lazy force region",
            )
        val expectedTypes =
            listOf(
                FlixLexer.DEF,
                FlixLexer.LET,
                FlixLexer.MATCH,
                FlixLexer.PUB,
                FlixLexer.ENUM,
                FlixLexer.ALIAS,
                FlixLexer.CASE,
                FlixLexer.IF,
                FlixLexer.ELSE,
                FlixLexer.TRY,
                FlixLexer.CATCH,
                FlixLexer.FOR,
                FlixLexer.FORALL,
                FlixLexer.YIELD,
                FlixLexer.INTO,
                FlixLexer.MOD,
                FlixLexer.MUT,
                FlixLexer.LAZY,
                FlixLexer.FORCE,
                FlixLexer.REGION,
            )
        assertEquals(expectedTypes, tokens.map { it.type })
    }

    @Test
    fun testSpecialSpellingKeywords() {
        val tokens = lexDefaultChannel("Array# List# Map# Set# Vector# choose*")
        val expectedTypes =
            listOf(
                FlixLexer.ARRAY_HASH,
                FlixLexer.LIST_HASH,
                FlixLexer.MAP_HASH,
                FlixLexer.SET_HASH,
                FlixLexer.VECTOR_HASH,
                FlixLexer.CHOOSE_STAR,
            )
        assertEquals(expectedTypes, tokens.map { it.type })
    }

    @Test
    fun testKeywordTailGuard() {
        // let! has ! as name char, so it must be lexed as a single NAME_LOWERCASE
        val tokens1 = lexDefaultChannel("let!")
        assertEquals(1, tokens1.size)
        assertEquals(FlixLexer.NAME_LOWERCASE, tokens1[0].type)
        assertEquals("let!", tokens1[0].text)

        // Array#x must fall back to Array (NAME_UPPERCASE), # (HASH), x (NAME_LOWERCASE)
        val tokens2 = lexDefaultChannel("Array#x")
        assertEquals(3, tokens2.size)
        assertEquals(FlixLexer.NAME_UPPERCASE, tokens2[0].type)
        assertEquals("Array", tokens2[0].text)
        assertEquals(FlixLexer.HASH, tokens2[1].type)
        assertEquals(FlixLexer.NAME_LOWERCASE, tokens2[2].type)
        assertEquals("x", tokens2[2].text)
    }

    @Test
    fun testEscapedKeyword() {
        val tokens = lexDefaultChannel("\$let")
        assertEquals(1, tokens.size)
        assertEquals(FlixLexer.NAME_LOWERCASE, tokens[0].type)
        assertEquals("let", tokens[0].text)
    }

    @Test
    fun testUnderscoreDispatcher() {
        val tokens = lexDefaultChannel("_foo _1 _ _⊆ _+")
        assertEquals(6, tokens.size)

        // _foo -> NAME_LOWERCASE
        assertEquals(FlixLexer.NAME_LOWERCASE, tokens[0].type)
        assertEquals("_foo", tokens[0].text)

        // _1 -> UNDERSCORE, INT_LITERAL
        assertEquals(FlixLexer.UNDERSCORE, tokens[1].type)
        assertEquals("_", tokens[1].text)
        assertEquals(FlixLexer.INT_LITERAL, tokens[2].type)
        assertEquals("1", tokens[2].text)

        // _ -> UNDERSCORE
        assertEquals(FlixLexer.UNDERSCORE, tokens[3].type)
        assertEquals("_", tokens[3].text)

        // _⊆ -> NAME_MATH
        assertEquals(FlixLexer.NAME_MATH, tokens[4].type)
        assertEquals("_⊆", tokens[4].text)

        // _+ -> GENERIC_OPERATOR
        assertEquals(FlixLexer.GENERIC_OPERATOR, tokens[5].type)
        assertEquals("_+", tokens[5].text)
    }

    @Test
    fun testWhitespaceSensitiveArrow() {
        // tight arrow: no whitespace around
        val tight = lexDefaultChannel("a->b")
        assertEquals(3, tight.size)
        assertEquals(FlixLexer.NAME_LOWERCASE, tight[0].type)
        assertEquals(FlixLexer.ARROW_TIGHT, tight[1].type)
        assertEquals(FlixLexer.NAME_LOWERCASE, tight[2].type)

        // spaced arrow variants
        val spaced1 = lexDefaultChannel("a -> b")
        assertEquals(FlixLexer.ARROW_WS, spaced1[1].type)

        val spaced2 = lexDefaultChannel("a ->b")
        assertEquals(FlixLexer.ARROW_WS, spaced2[1].type)

        val spaced3 = lexDefaultChannel("a-> b")
        assertEquals(FlixLexer.ARROW_WS, spaced3[1].type)

        // start and end of input boundaries
        val startBoundary = lexDefaultChannel("->b")
        assertEquals(FlixLexer.ARROW_WS, startBoundary[0].type)

        val endBoundary = lexDefaultChannel("a->")
        assertEquals(FlixLexer.ARROW_WS, endBoundary[1].type)
    }

    @Test
    fun testWhitespaceSensitiveDot() {
        // tight dot
        val tight = lexDefaultChannel("a.b")
        assertEquals(FlixLexer.DOT, tight[1].type)

        // trailing whitespace
        val trailing = lexDefaultChannel("a. b")
        assertEquals(FlixLexer.DOT_WS, trailing[1].type)

        // leading whitespace
        val leading = lexDefaultChannel("a .b")
        assertEquals(FlixLexer.FREE_DOT, leading[1].type)

        // leading and trailing whitespace
        val bothSpace = lexDefaultChannel("a . b")
        assertEquals(FlixLexer.FREE_DOT, bothSpace[1].type)

        // start of input boundary
        val startDot = lexDefaultChannel(".b")
        assertEquals(FlixLexer.FREE_DOT, startDot[0].type)

        // end of input boundary
        val endDot = lexDefaultChannel("a.")
        assertEquals(FlixLexer.DOT_WS, endDot[1].type)
    }

    @Test
    fun testOperatorRunsAndAllReservedOperators() {
        // <-- is one GENERIC_OPERATOR run, not <- and -
        val op1 = lexDefaultChannel("<--")
        assertEquals(1, op1.size)
        assertEquals(FlixLexer.GENERIC_OPERATOR, op1[0].type)
        assertEquals("<--", op1[0].text)

        // test all 18 reserved operator spellings
        val reservedInput = "! != & * + - < <+> <- <= <=> = == => > >= ^ |"
        val tokens = lexDefaultChannel(reservedInput)
        val expected =
            listOf(
                FlixLexer.BANG,
                FlixLexer.BANG_EQUAL,
                FlixLexer.AMPERSAND,
                FlixLexer.STAR,
                FlixLexer.PLUS,
                FlixLexer.MINUS,
                FlixLexer.ANGLE_L,
                FlixLexer.ANGLED_PLUS,
                FlixLexer.ARROW_THIN_L,
                FlixLexer.ANGLE_L_EQUAL,
                FlixLexer.ANGLED_EQUAL,
                FlixLexer.EQUAL,
                FlixLexer.EQUAL_EQUAL,
                FlixLexer.ARROW_THICK_R,
                FlixLexer.ANGLE_R,
                FlixLexer.ANGLE_R_EQUAL,
                FlixLexer.CARET,
                FlixLexer.BAR,
            )
        assertEquals(expected, tokens.map { it.type })

        // colon operators
        val colons = lexDefaultChannel(":: ::: :-")
        assertEquals(
            listOf(FlixLexer.COLON_COLON, FlixLexer.COLON_COLON_COLON, FlixLexer.COLON_MINUS),
            colons.map { it.type },
        )

        // custom operators
        val op3 = lexDefaultChannel("|> >> >=>")
        assertEquals(
            listOf(
                FlixLexer.GENERIC_OPERATOR,
                FlixLexer.GENERIC_OPERATOR,
                FlixLexer.GENERIC_OPERATOR,
            ),
            op3.map {
                it.type
            },
        )
    }

    @Test
    fun testStringInterpolation() {
        val input = "\"hello \${x} world\""
        val tokens = lexDefaultChannel(input)

        // Token sequence: STRING_START, STRING_CONTENT ("hello "), INTERPOLATION_START ("${"), NAME_LOWERCASE ("x"), INTERPOLATION_END ("}"), STRING_CONTENT (" world"), STRING_END
        assertEquals(7, tokens.size)
        assertEquals(FlixLexer.STRING_START, tokens[0].type)
        assertEquals(FlixLexer.STRING_CONTENT, tokens[1].type)
        assertEquals("hello ", tokens[1].text)
        assertEquals(FlixLexer.INTERPOLATION_START, tokens[2].type)
        assertEquals(FlixLexer.NAME_LOWERCASE, tokens[3].type)
        assertEquals("x", tokens[3].text)
        assertEquals(FlixLexer.INTERPOLATION_END, tokens[4].type)
        assertEquals(FlixLexer.STRING_CONTENT, tokens[5].type)
        assertEquals(" world", tokens[5].text)
        assertEquals(FlixLexer.STRING_END, tokens[6].type)
    }

    @Test
    fun testNestedBracesInInterpolation() {
        val input = "\"result: \${ { x: 1 } }\""
        val tokens = lexDefaultChannel(input)

        val types = tokens.map { it.type }
        assertEquals(
            listOf(
                FlixLexer.STRING_START,
                FlixLexer.STRING_CONTENT,
                FlixLexer.INTERPOLATION_START,
                FlixLexer.LBRACE,
                FlixLexer.NAME_LOWERCASE,
                FlixLexer.COLON,
                FlixLexer.INT_LITERAL,
                FlixLexer.RBRACE,
                FlixLexer.INTERPOLATION_END,
                FlixLexer.STRING_END,
            ),
            types,
        )
    }

    @Test
    fun testUnterminatedStringEOFReset() {
        val stream = CharStreams.fromString("\"unterminated \${x")
        val lexer = FlixLexer(stream)
        val tokens = mutableListOf<Token>()
        while (true) {
            val t = lexer.nextToken()
            tokens.add(t)
            if (t.type == Token.EOF) break
        }
        assertEquals(Token.EOF, tokens.last().type)
    }

    @Test
    fun testStandaloneBracesOutsideInterpolation() {
        val tokens = lexDefaultChannel("{ x }")
        assertEquals(
            listOf(FlixLexer.LBRACE, FlixLexer.NAME_LOWERCASE, FlixLexer.RBRACE),
            tokens.map { it.type },
        )
    }

    @Test
    fun testCommentsAndChannels() {
        val input =
            """
            // line comment
            /// doc comment
            //// line comment four slashes
            /* block /* nested */ comment */
            code
            """.trimIndent()

        val tokens = lexAllChannels(input)

        val lineComment = tokens.find { it.text == "// line comment" }
        assertEquals(FlixLexer.COMMENTS, lineComment?.channel)

        val docComment = tokens.find { it.text == "/// doc comment" }
        assertEquals(FlixLexer.DOC_COMMENTS, docComment?.channel)

        val fourSlash = tokens.find { it.text == "//// line comment four slashes" }
        assertEquals(FlixLexer.COMMENTS, fourSlash?.channel)

        val blockComment = tokens.find { it.text == "/* block /* nested */ comment */" }
        assertEquals(FlixLexer.COMMENTS, blockComment?.channel)

        val codeToken = tokens.find { it.text == "code" }
        assertEquals(Token.DEFAULT_CHANNEL, codeToken?.channel)
    }

    @Test
    fun testLiterals() {
        val tokens = lexDefaultChannel("123 123i8 123i64 123ii 0x1A 123.45 123.45f32 'a' '\\n'")
        val expected =
            listOf(
                FlixLexer.INT_LITERAL,
                FlixLexer.INT_LITERAL,
                FlixLexer.INT_LITERAL,
                FlixLexer.INT_LITERAL,
                FlixLexer.HEX_LITERAL,
                FlixLexer.FLOAT_LITERAL,
                FlixLexer.FLOAT_LITERAL,
                FlixLexer.CHAR_LITERAL,
                FlixLexer.CHAR_LITERAL,
            )
        assertEquals(expected, tokens.map { it.type })
    }
}
