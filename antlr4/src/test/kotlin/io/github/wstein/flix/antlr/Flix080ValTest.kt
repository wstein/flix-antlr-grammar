package io.github.wstein.flix.antlr

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Flix080ValTest {
    @Test
    fun valIsAKeywordWithoutSplittingLongerIdentifiers() {
        val lexer = FlixLexer(CharStreams.fromString("val value val!"))
        val tokens = lexer.allTokens.filter { it.channel == 0 }
        assertEquals(listOf("val", "value", "val!"), tokens.map { it.text })
        assertEquals("VAL", lexer.vocabulary.getSymbolicName(tokens[0].type))
        assertNotEquals(tokens[0].type, tokens[1].type)
        assertEquals(tokens[1].type, tokens[2].type)
    }

    @Test
    fun moduleValDeclarationAndLocalLetParse() {
        for (source in listOf("mod M { pub val field: Int32 = 42 }", "def f(): Int32 = let x = 42; x")) {
            val parser = FlixParser(CommonTokenStream(FlixLexer(CharStreams.fromString(source))))
            parser.removeErrorListeners()
            parser.compilationUnit()
            assertEquals(0, parser.numberOfSyntaxErrors, source)
        }
    }

    @Test
    fun valDeclarationRequiresEqualsAndInitializer() {
        for (source in listOf("val field: Int32", "val field: Int32 42", "val field: Int32 =")) {
            val parser = FlixParser(CommonTokenStream(FlixLexer(CharStreams.fromString(source))))
            parser.removeErrorListeners()
            parser.compilationUnit()
            assertTrue(parser.numberOfSyntaxErrors > 0, source)
        }
    }

    @Test
    fun valCannotBeAnUnescapedFunctionName() {
        val parser = FlixParser(CommonTokenStream(FlixLexer(CharStreams.fromString("def val(): Int32 = 42"))))
        parser.removeErrorListeners()
        parser.compilationUnit()
        assertTrue(parser.numberOfSyntaxErrors > 0)
    }

    @Test
    fun valCannotBeAnUnescapedParameter() {
        val parser = FlixParser(CommonTokenStream(FlixLexer(CharStreams.fromString("def f(val: Int32): Int32 = val"))))
        parser.removeErrorListeners()
        parser.compilationUnit()
        assertTrue(parser.numberOfSyntaxErrors > 0)
    }
}
