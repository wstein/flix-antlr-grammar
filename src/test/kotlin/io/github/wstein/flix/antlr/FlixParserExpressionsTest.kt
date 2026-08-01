package io.github.wstein.flix.antlr

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FlixParserExpressionsTest {
    private fun parseExpr(input: String): FlixParser.ExprContext {
        val stream = CharStreams.fromString(input)
        val lexer = FlixLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = FlixParser(tokens)
        return parser.expr()
    }

    @Test
    fun testOperatorPrecedenceArithmetic() {
        // 1 + 2 * 3 parses as 1 + (2 * 3)
        val ctx = parseExpr("1 + 2 * 3")
        assertNull(ctx.exception)
        assertInstanceOf(FlixParser.AddExprContext::class.java, ctx)
        val addCtx = ctx as FlixParser.AddExprContext
        assertEquals("1", addCtx.expr(0).text)
        assertInstanceOf(FlixParser.MultExprContext::class.java, addCtx.expr(1))
    }

    @Test
    fun testUserDefinedOperatorPrecedence() {
        // a |> b + c parses as (a |> b) + c because user operators (level 11) bind tighter than + (level 6)
        val ctx = parseExpr("a |> b + c")
        assertNull(ctx.exception)
        assertInstanceOf(FlixParser.AddExprContext::class.java, ctx)
        val addCtx = ctx as FlixParser.AddExprContext
        assertInstanceOf(FlixParser.UserOpExprContext::class.java, addCtx.expr(0))
        assertEquals("c", addCtx.expr(1).text)
    }

    @Test
    fun testConsRightAssociativity() {
        // x :: y :: z parses as x :: (y :: z)
        val ctx = parseExpr("x :: y :: z")
        assertNull(ctx.exception)
        assertInstanceOf(FlixParser.ConsExprContext::class.java, ctx)
        val consCtx = ctx as FlixParser.ConsExprContext
        assertEquals("x", consCtx.expr(0).text)
        assertInstanceOf(FlixParser.ConsExprContext::class.java, consCtx.expr(1))
    }

    @Test
    fun testDiscardPrecedence() {
        // discard a <*> b parses as discard (a <*> b) because discard (level 9) < user operator (level 11)
        val ctx = parseExpr("discard a <*> b")
        assertNull(ctx.exception)
        assertInstanceOf(FlixParser.DiscardExprContext::class.java, ctx)
        val discardCtx = ctx as FlixParser.DiscardExprContext
        assertInstanceOf(FlixParser.UserOpExprContext::class.java, discardCtx.expr())
    }

    @Test
    fun testIfExpr() {
        val ctx = parseExpr("if (x) y else z")
        assertNull(ctx.exception)
        assertInstanceOf(FlixParser.IfExprContext::class.java, ctx)
        val ifCtx = ctx as FlixParser.IfExprContext
        assertEquals("x", ifCtx.expr(0).text)
        assertEquals("y", ifCtx.expr(1).text)
        assertEquals("z", ifCtx.expr(2).text)
    }

    @Test
    fun testMatchExpr() {
        val input =
            """
            match x {
                case Some(v) => v
                case None => 0
            }
            """.trimIndent()
        val ctx = parseExpr(input)
        assertNull(ctx.exception)
        assertInstanceOf(FlixParser.MatchExprContext::class.java, ctx)
        val matchCtx = ctx as FlixParser.MatchExprContext
        assertEquals(2, matchCtx.matchCase().size)
    }

    @Test
    fun testLetExpr() {
        val ctx = parseExpr("let x = 1; x + 1")
        assertNull(ctx.exception)
        assertInstanceOf(FlixParser.LetExprContext::class.java, ctx)
        val letCtx = ctx as FlixParser.LetExprContext
        assertEquals("x", letCtx.pattern().text)
        assertEquals("1", letCtx.expr(0).text)
    }

    @Test
    fun testLambdaExpr() {
        val ctx = parseExpr("x -> x + 1")
        assertNull(ctx.exception)
        assertInstanceOf(FlixParser.LambdaExprContext::class.java, ctx)
        val lambda = ctx as FlixParser.LambdaExprContext
        assertEquals("x", lambda.lambdaParams().text)
    }
}
