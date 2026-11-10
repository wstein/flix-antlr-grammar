package io.github.wstein.flix.antlr

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Flix079Test {
    @Test
    fun currentSyntax() {
        val sources =
            listOf(
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
            )
        for (source in sources) {
            val parser = FlixParser(CommonTokenStream(FlixLexer(CharStreams.fromString(source))))
            parser.compilationUnit()
            assertEquals(0, parser.numberOfSyntaxErrors, source)
        }
    }

    @Test
    fun namedArgumentsInSuperCalls() {
        for (call in listOf("super(x = 1)", "super.method(x = 1)")) {
            val source = "def f(): Unit = $call"
            val parser = FlixParser(CommonTokenStream(FlixLexer(CharStreams.fromString(source))))
            parser.removeErrorListeners()
            parser.compilationUnit()
            assertEquals(0, parser.numberOfSyntaxErrors, source)
        }
    }

    @Test
    fun callAlternativesKeepTheirRolesAndArgumentLists() {
        val calls =
            listOf(
                "f(x = 1)" to "ApplyExprContext",
                "obj.method(x = 1)" to "InvokeMethodExprContext",
                "obj.field" to "GetFieldExprContext",
                "new Foo(x = 1)" to "InvokeConstructorExprContext",
                "super.method(x = 1)" to "InvokeSuperMethodExprContext",
                "super(x = 1)" to "InvokeSuperConstructorExprContext",
                "xvar Foo(x = 1)" to "ExtTagExprContext",
            )
        for ((source, role) in calls) {
            val parser = FlixParser(CommonTokenStream(FlixLexer(CharStreams.fromString(source))))
            val expression = parser.expr()
            assertEquals(0, parser.numberOfSyntaxErrors, source)
            assertEquals(role, expression.javaClass.simpleName, source)
            val argumentLists =
                (0 until expression.childCount).count {
                    expression.getChild(it) is FlixParser.ArgumentListContext
                }
            assertEquals(if (role == "GetFieldExprContext") 0 else 1, argumentLists, source)
        }
    }

    @Test
    fun typeBoundariesFollowParser2() {
        val positive =
            listOf(
                "type alias T = {}",
                "type alias T = {|}",
                "type alias T[r] = {| r}",
                "type alias T[r] = {x = Int32 | Rows[r]}",
                "type alias T = ()",
                "type alias T[r] = (x = Int32 | Rows[r])",
                "type alias T[r] = (| Rows[r])",
                "type alias T = (Int32, Bool)",
            )
        val negative =
            listOf(
                "type alias T[r] = {| r",
                "type alias T = {",
                "def f(): Unit = super(1,)",
            )
        for (source in positive + negative) {
            val parser = FlixParser(CommonTokenStream(FlixLexer(CharStreams.fromString(source))))
            parser.removeErrorListeners()
            parser.compilationUnit()
            assertEquals(source in positive, parser.numberOfSyntaxErrors == 0, source)
        }
    }

    @Test
    fun tightConsHasItsOwnToken() {
        for ((source, expected) in listOf(
            "a::b" to FlixLexer.COLON_COLON_TIGHT,
            "a ::b" to FlixLexer.COLON_COLON,
            "a:: b" to FlixLexer.COLON_COLON,
            "::" to FlixLexer.COLON_COLON,
        )) {
            val tokens = FlixLexer(CharStreams.fromString(source)).allTokens
            assertEquals(expected, tokens.first { it.text == "::" }.type, source)
        }
    }

    @Test
    fun directGroupedUseNeedsAPackage() {
        val parser = FlixParser(CommonTokenStream(FlixLexer(CharStreams.fromString("use {Game, Board}"))))
        parser.removeErrorListeners()
        parser.compilationUnit()
        assertTrue(parser.numberOfSyntaxErrors > 0)
    }
}
