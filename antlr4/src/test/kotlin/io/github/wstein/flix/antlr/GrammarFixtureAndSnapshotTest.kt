package io.github.wstein.flix.antlr

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GrammarFixtureAndSnapshotTest {
    private val rootDir = File("..").canonicalFile
    private val fixturesDir = File(rootDir, "fixtures")
    private val positiveDir = File(fixturesDir, "positive")
    private val negativeDir = File(fixturesDir, "negative")
    private val snapshotsDir = File(fixturesDir, "snapshots")

    @Test
    fun testPositiveFixturesAndSnapshots() {
        assertTrue(positiveDir.exists(), "fixtures/positive directory should exist")
        val positiveFiles = positiveDir.listFiles { _, name -> name.endsWith(".flix") } ?: emptyArray()
        assertTrue(positiveFiles.isNotEmpty(), "fixtures/positive should contain .flix test files")

        if (!snapshotsDir.exists()) {
            snapshotsDir.mkdirs()
        }

        for (file in positiveFiles.sortedBy { it.name }) {
            val stream = CharStreams.fromPath(file.toPath())
            val lexer = FlixLexer(stream)
            val tokens = CommonTokenStream(lexer)
            val parser = FlixParser(tokens)
            val errors = mutableListOf<String>()
            parser.removeErrorListeners()
            parser.addErrorListener(
                object : org.antlr.v4.runtime.BaseErrorListener() {
                    override fun syntaxError(
                        recognizer: org.antlr.v4.runtime.Recognizer<*, *>?,
                        offendingSymbol: Any?,
                        line: Int,
                        charPositionInLine: Int,
                        msg: String?,
                        e: org.antlr.v4.runtime.RecognitionException?,
                    ) {
                        errors.add("${file.name}:$line:$charPositionInLine: $msg")
                    }
                },
            )
            val tree = parser.compilationUnit()

            assertEquals(
                0,
                parser.numberOfSyntaxErrors,
                "Fixture ${file.name} failed to parse with errors: $errors",
            )

            val treeString = tree.toStringTree(parser)
            val snapFile = File(snapshotsDir, "${file.nameWithoutExtension}.snap")

            // Regeneration is opt-in via -Dsnapshots.update=true. Writing whenever the file is
            // merely absent would let a deleted snapshot silently accept any tree shape.
            val update = System.getProperty("snapshots.update") == "true"
            assertTrue(
                snapFile.exists() || update,
                "Missing snapshot ${snapFile.name}. A deleted snapshot must not be regenerated " +
                    "silently, or structural-regression protection is bypassed. " +
                    "Re-run with -Dsnapshots.update=true to create it deliberately.",
            )
            if (snapFile.exists() && !update) {
                assertEquals(
                    snapFile.readText().trim(),
                    treeString,
                    "Snapshot mismatch for ${file.name}. Structural AST regression detected! " +
                        "Re-run with -Dsnapshots.update=true if the change is intended.",
                )
            } else {
                snapFile.writeText(treeString)
            }
        }
    }

    @Test
    fun snapshotsHaveNoOrphans() {
        // An orphan snapshot means a fixture was renamed or removed and the stale tree shape
        // is still being carried around as if it were covered.
        val fixtures =
            positiveDir
                .listFiles { _, n -> n.endsWith(".flix") }
                .orEmpty()
                .map { it.nameWithoutExtension }
                .toSet()
        val snapshots =
            snapshotsDir
                .listFiles { _, n -> n.endsWith(".snap") }
                .orEmpty()
                .map { it.nameWithoutExtension }
                .toSet()
        assertEquals(emptySet<String>(), snapshots - fixtures, "orphan snapshots")
    }

    @Test
    fun testNegativeFixtures() {
        assertTrue(negativeDir.exists(), "fixtures/negative directory should exist")
        val negativeFiles = negativeDir.listFiles { _, name -> name.endsWith(".flix") } ?: emptyArray()
        assertTrue(negativeFiles.isNotEmpty(), "fixtures/negative should contain .flix test files")

        for (file in negativeFiles.sortedBy { it.name }) {
            // Each fixture declares where its first error must land, as
            // `// expect-error: <line>:<column>` on the first line. Asserting only that some
            // error occurred is close to worthless: `def f(::: = {` fails under almost any
            // grammar, including one for a different language, so it cannot detect
            // over-permissiveness at any particular place.
            val directive = file.readLines().firstOrNull().orEmpty()
            val expected = Regex("// expect-error: (\\d+):(\\d+)").find(directive)
            assertNotNull(expected, "${file.name} must start with `// expect-error: <line>:<col>`")

            val errors = mutableListOf<Pair<Int, Int>>()
            val parser = FlixParser(CommonTokenStream(FlixLexer(CharStreams.fromPath(file.toPath()))))
            parser.removeErrorListeners()
            parser.addErrorListener(
                object : BaseErrorListener() {
                    override fun syntaxError(
                        recognizer: Recognizer<*, *>?,
                        offendingSymbol: Any?,
                        line: Int,
                        charPositionInLine: Int,
                        msg: String?,
                        e: RecognitionException?,
                    ) {
                        errors += line to charPositionInLine
                    }
                },
            )
            parser.compilationUnit()

            assertTrue(
                errors.isNotEmpty(),
                "Negative fixture ${file.name} expected syntax errors but parsed cleanly",
            )
            val want = expected!!.groupValues[1].toInt() to expected.groupValues[2].toInt()
            assertEquals(
                want,
                errors.first(),
                "${file.name}: first error moved. Update the directive only if the new position " +
                    "is correct; a moved error often means the grammar became more permissive.",
            )
        }
    }
}
