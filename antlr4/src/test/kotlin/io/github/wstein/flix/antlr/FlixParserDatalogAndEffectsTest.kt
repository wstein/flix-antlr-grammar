package io.github.wstein.flix.antlr

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FlixParserDatalogAndEffectsTest {
    private fun parseExpr(input: String): FlixParser.ExprContext {
        val stream = CharStreams.fromString(input)
        val lexer = FlixLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = FlixParser(tokens)
        return parser.expr()
    }

    private fun parseCU(input: String): FlixParser.CompilationUnitContext {
        val stream = CharStreams.fromString(input)
        val lexer = FlixLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = FlixParser(tokens)
        return parser.compilationUnit()
    }

    @Test
    fun testDatalogFactAndRule() {
        val input =
            """
            Edge("a", "b").
            Path(x, z) :- Path(x, y), Edge(y, z).
            """.trimIndent()

        val cu = parseCU(input)
        assertNull(cu.exception)
        assertEquals(2, cu.declaration().size)

        val fact = cu.declaration(0).datalogConstraint()
        assertNotNull(fact)
        assertEquals("Edge", fact.qname().text)

        val rule = cu.declaration(1).datalogConstraint()
        assertNotNull(rule)
        assertEquals("Path", rule.qname().text)
        assertEquals(2, rule.datalogBody().size)
    }

    @Test
    fun testFixpointSolveAndQuery() {
        val solveCtx = parseExpr("solve p")
        assertNull(solveCtx.exception)
        assertInstanceOf(FlixParser.SolveExprContext::class.java, solveCtx)

        val queryCtx = parseExpr("query p select (x, y) from P(x, y)")
        assertNull(queryCtx.exception)
        assertInstanceOf(FlixParser.QueryExprContext::class.java, queryCtx)
    }

    @Test
    fun testInjectAndProject() {
        val injectCtx = parseExpr("inject p")
        assertNull(injectCtx.exception)
        assertInstanceOf(FlixParser.InjectExprContext::class.java, injectCtx)

        val projectCtx = parseExpr("project(x, y) p")
        assertNull(projectCtx.exception)
        assertInstanceOf(FlixParser.ProjectExprContext::class.java, projectCtx)
    }

    @Test
    fun testEffectDoTryRun() {
        val doCtx = parseExpr("do Logger.log(\"msg\")")
        assertNull(doCtx.exception)
        assertInstanceOf(FlixParser.DoOpExprContext::class.java, doCtx)

        val tryCtx = parseExpr("try e with def log(msg) => ()")
        assertNull(tryCtx.exception)
        assertInstanceOf(FlixParser.TryWithExprContext::class.java, tryCtx)

        val runCtx = parseExpr("run e")
        assertNull(runCtx.exception)
        assertInstanceOf(FlixParser.RunExprContext::class.java, runCtx)
    }
}
