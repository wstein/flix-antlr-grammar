package io.github.wstein.flix.antlr

import io.github.wstein.flix.antlr.cli.parseFile
import io.github.wstein.flix.antlr.cli.runValidation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Parse-rate gate over a real Flix checkout.
 *
 * Hand-written snippets cannot establish that a grammar covers a language: an earlier version of
 * this test parsed two inline samples and reported success while the grammar rejected nine out of
 * ten real files. The gate therefore runs against an actual corpus and ratchets against a committed
 * baseline.
 *
 * The corpus location comes from `-Dflix.corpus=<dir>` or the `FLIX_CORPUS` environment variable,
 * falling back to a sibling `flix-fork` checkout. When no corpus is present the test is *skipped*
 * rather than passed, so a missing corpus can never be mistaken for coverage.
 */
class CorpusCoverageTest {
    private val baselineFile = File("../fixtures/corpus-baseline.json")

    private fun corpusDir(): File? {
        val configured = System.getProperty("flix.corpus") ?: System.getenv("FLIX_CORPUS")
        val candidates =
            listOfNotNull(
                configured,
                "${System.getProperty("user.home")}/github.com/wstein/flix-fork/main",
            )
        return candidates.map(::File).firstOrNull { it.isDirectory }
    }

    /** Minimal field read; the baseline file is ours and has a fixed shape. */
    private fun baselineRate(): Double {
        val match = Regex("\"rate\"\\s*:\\s*([0-9.]+)").find(baselineFile.readText())
        requireNotNull(match) { "corpus-baseline.json is missing a numeric \"rate\" field" }
        return match.groupValues[1].toDouble()
    }

    @Test
    fun corpusParseRateMeetsBaseline() {
        val corpus = corpusDir()
        assumeTrue(corpus != null, "No Flix corpus available; set -Dflix.corpus=<dir> to enable")

        val files = corpus!!.walkTopDown().filter { it.isFile && it.extension == "flix" }.toList()
        assumeTrue(files.isNotEmpty(), "Corpus directory contains no .flix files")

        val parsed = files.count { runCatching { parseFile(it).success }.getOrDefault(false) }
        val rate = parsed.toDouble() / files.size
        val baseline = baselineRate()

        println(
            "corpus: %d / %d parsed (%.2f%%), baseline %.2f%%"
                .format(parsed, files.size, rate * 100, baseline * 100),
        )

        // Tolerance absorbs corpus churn only, not grammar regressions.
        assertTrue(
            rate >= baseline - 0.005,
            "Corpus parse rate regressed: %.4f < baseline %.4f. ".format(rate, baseline) +
                "Fix the grammar rather than lowering fixtures/corpus-baseline.json.",
        )

        if (rate > baseline + 0.005) {
            println(
                "Parse rate improved to %.4f; raise \"rate\" in fixtures/corpus-baseline.json to lock it in."
                    .format(rate),
            )
        }
    }

    @Test
    fun cliValidatesFilesAndDirectories(
        @TempDir tempDir: File,
    ) {
        val validDir = File(tempDir, "validDir").apply { mkdirs() }
        val invalidDir = File(tempDir, "invalidDir").apply { mkdirs() }

        val validFile = File(validDir, "valid.flix").apply { writeText("def main(): Unit = ()") }
        val invalidFile = File(invalidDir, "invalid.flix").apply { writeText("def (()) ::: =") }

        assertEquals(1, runValidation(emptyArray()))
        assertEquals(1, runValidation(arrayOf(File(tempDir, "nonexistent").path)))
        assertEquals(0, runValidation(arrayOf(validFile.path)))
        assertEquals(0, runValidation(arrayOf(validDir.path)))
        assertEquals(1, runValidation(arrayOf(invalidDir.path)))

        val badResult = parseFile(invalidFile)
        assertFalse(badResult.success)
        assertTrue(badResult.errorCount > 0)
    }
}
