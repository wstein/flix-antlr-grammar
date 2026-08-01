package io.github.wstein.flix.antlr

import org.antlr.v4.runtime.CharStreams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Pins the lexer's keyword table to `fixtures/keywords.txt`.
 *
 * The table originally drifted in both directions at once: 22 real Flix keywords were absent
 * while 24 that do not exist in the language were defined, including `namespace` and `class` from
 * a Flix version predating `mod` and `trait`. Nothing enforced the correspondence, so nothing
 * caught it. This test checks set equality in both directions, because a one-directional check
 * would catch only half of that defect.
 *
 * Regenerate `fixtures/keywords.txt` from `Lexer.scala:49-139` when bumping the reference
 * compiler, as a deliberate manual step rather than as part of the build.
 */
class FlixKeywordTableTest {
    private val expected: Set<String> =
        File("../fixtures/keywords.txt")
            .readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .toSet()

    /** Every keyword the lexer defines, read back from the generated token vocabulary. */
    private fun lexerKeywords(): Set<String> =
        (0..FlixLexer.VOCABULARY.maxTokenType)
            .mapNotNull { FlixLexer.VOCABULARY.getLiteralName(it) }
            .map { it.trim('\'') }
            // Keywords are the multi-character word-like literals. Single letters (the debug
            // interpolator `d`) and the wildcard `_` are tokens but not keywords.
            .filter { it.length > 1 && it[0].isLetter() }
            .filter { it.all { c -> c.isLetterOrDigit() || c == '_' || c == '#' || c == '*' } }
            .toSet()

    @Test
    fun lexerDefinesExactlyTheDocumentedKeywords() {
        val actual = lexerKeywords()

        val missing = (expected - actual).sorted()
        val phantom = (actual - expected).sorted()

        assertTrue(
            missing.isEmpty(),
            "Keywords in fixtures/keywords.txt but absent from FlixLexer.g4: $missing",
        )
        assertTrue(
            phantom.isEmpty(),
            "Keywords defined in FlixLexer.g4 that are not Flix keywords: $phantom",
        )
        assertEquals(84, expected.size, "Flix 0.75.1 has 84 keywords")
    }

    @Test
    fun nameCharactersDoNotTerminateKeywords() {
        // `!` and `$` are name characters, so these are identifiers, not keyword-plus-operator.
        for (text in listOf("let!", "def\$", "case_", "xor1")) {
            val tokens = tokenTypes(text)
            assertEquals(
                listOf(FlixLexer.NAME_LOWERCASE),
                tokens,
                "'$text' should lex as a single identifier",
            )
        }
    }

    @Test
    fun holesAreTokenized() {
        assertEquals(listOf(FlixLexer.HOLE_ANONYMOUS), tokenTypes("???"))
        assertEquals(listOf(FlixLexer.HOLE_NAMED), tokenTypes("?foo"))
        assertEquals(listOf(FlixLexer.HOLE_VARIABLE), tokenTypes("x?"))
    }

    private fun tokenTypes(text: String): List<Int> {
        val lexer = FlixLexer(CharStreams.fromString(text))
        return lexer.allTokens.map { it.type }
    }
}
