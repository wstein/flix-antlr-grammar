package io.github.wstein.flix.antlr

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun testNegativeFixtures() {
        assertTrue(negativeDir.exists(), "fixtures/negative directory should exist")
        val negativeFiles = negativeDir.listFiles { _, name -> name.endsWith(".flix") } ?: emptyArray()
        assertTrue(negativeFiles.isNotEmpty(), "fixtures/negative should contain .flix test files")

        for (file in negativeFiles.sortedBy { it.name }) {
            val stream = CharStreams.fromPath(file.toPath())
            val lexer = FlixLexer(stream)
            val tokens = CommonTokenStream(lexer)
            val parser = FlixParser(tokens)
            parser.compilationUnit()

            assertTrue(
                parser.numberOfSyntaxErrors > 0,
                "Negative fixture ${file.name} expected syntax errors but parsed cleanly",
            )
        }
    }
}
