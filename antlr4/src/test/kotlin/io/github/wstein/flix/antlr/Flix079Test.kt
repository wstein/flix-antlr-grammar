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
