package io.github.wstein.flix.antlr

import io.github.wstein.flix.antlr.cli.parseFile
import io.github.wstein.flix.antlr.cli.runValidation
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTreeListener
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CorpusCoverageTest {
    @Test
    fun testCorpusParseRate(
        @TempDir tempDir: File,
    ) {
        val sample1 =
            File(tempDir, "sample1.flix").apply {
                writeText(
                    """
                    mod Main {
                        pub def main(): Int32 = {
                            let x = 1 + 2 * 3;
                            if (x > 5) x else 0
                        }
                    }
                    """.trimIndent(),
                )
            }

        val sample2 =
            File(tempDir, "sample2.flix").apply {
                writeText(
                    """
                    use Bool.{==>, <==>}

                    enum Option[t] {
                        case None,
                        case Some(t)
                    }

                    struct Point {
                        x: Int32,
                        y: Int32
                    }

                    def distance(p: Point): Int32 = p.x + p.y

                    Path(x, z) :- Path(x, y), Edge(y, z).
                    """.trimIndent(),
                )
            }

        val result1 = parseFile(sample1)
        assertTrue(result1.success, "sample1.flix should parse cleanly: ${result1.errors}")

        val result2 = parseFile(sample2)
        assertTrue(result2.success, "sample2.flix should parse cleanly: ${result2.errors}")
    }

    @Test
    fun testCliValidationRunner(
        @TempDir tempDir: File,
    ) {
        val validDir = File(tempDir, "validDir").apply { mkdirs() }
        val invalidDir = File(tempDir, "invalidDir").apply { mkdirs() }

        val validFile =
            File(validDir, "valid.flix").apply {
                writeText("def main(): Unit = ()")
            }

        val invalidFile =
            File(invalidDir, "invalid.flix").apply {
                writeText("def (()) ::: =")
            }

        assertEquals(1, runValidation(emptyArray()))
        assertEquals(1, runValidation(arrayOf(File(tempDir, "nonexistent").path)))
        assertEquals(0, runValidation(arrayOf(validFile.path)))
        assertEquals(0, runValidation(arrayOf(validDir.path)))
        assertEquals(1, runValidation(arrayOf(invalidDir.path)))

        val badResult = parseFile(invalidFile)
        assertFalse(badResult.success)
        assertTrue(badResult.errorCount > 0)
        assertEquals(1, runValidation(arrayOf(invalidFile.path)))
    }

    @Test
    fun testRuleReachability() {
        val code =
            """
            mod Test {
                pub def foo(x: Int32): Int32 = {
                    let y = x |> bar;
                    match y {
                        case Some(v) => v
                        case None => 0
                    }
                }
            }
            """.trimIndent()

        val stream = CharStreams.fromString(code)
        val lexer = FlixLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = FlixParser(tokens)
        val tree = parser.compilationUnit()

        var visitedNodes = 0
        val walker = ParseTreeWalker()
        val listener =
            object : ParseTreeListener {
                override fun visitTerminal(node: org.antlr.v4.runtime.tree.TerminalNode?) {
                    visitedNodes++
                }

                override fun visitErrorNode(node: org.antlr.v4.runtime.tree.ErrorNode?) {}

                override fun enterEveryRule(ctx: org.antlr.v4.runtime.ParserRuleContext?) {
                    visitedNodes++
                }

                override fun exitEveryRule(ctx: org.antlr.v4.runtime.ParserRuleContext?) {}
            }

        walker.walk(listener, tree)
        assertTrue(visitedNodes > 10, "Walker should traverse CST nodes")
        assertEquals(0, parser.numberOfSyntaxErrors)
    }
}
