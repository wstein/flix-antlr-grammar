package io.github.wstein.flix.antlr

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Datalog, fixpoint and effect-handler forms.
 *
 * Datalog constraints are not top-level declarations in Flix: they appear inside a `#{ ... }`
 * constraint set, which is an expression. Having them as a declaration alternative made every
 * unmatched declaration retry as a constraint and report the constraint's expected set.
 */
class FlixParserDatalogAndEffectsTest {
    /**
     * Parses an expression and requires a clean, complete parse.
     *
     * Asserting only `ctx.exception == null` is too weak: ANTLR recovers from errors and still
     * returns a context with no exception attached. It also accepts a prefix, so a rule that
     * consumed half the input would pass. Collect diagnostics from both the lexer and the
     * parser, require none, and require that the whole input was consumed.
     */
    private fun parseExpr(input: String): FlixParser.ExprContext {
        val errors = mutableListOf<String>()
        val listener =
            object : BaseErrorListener() {
                override fun syntaxError(
                    recognizer: Recognizer<*, *>?,
                    offendingSymbol: Any?,
                    line: Int,
                    charPositionInLine: Int,
                    msg: String?,
                    e: RecognitionException?,
                ) {
                    errors += "$line:$charPositionInLine $msg"
                }
            }
        val lexer = FlixLexer(CharStreams.fromString(input))
        lexer.removeErrorListeners()
        lexer.addErrorListener(listener)
        val tokens = CommonTokenStream(lexer)
        val parser = FlixParser(tokens)
        parser.removeErrorListeners()
        parser.addErrorListener(listener)

        val ctx = parser.expr()
        assertTrue(errors.isEmpty(), "'$input' produced diagnostics: $errors")
        assertEquals(
            Token.EOF,
            tokens.LA(1),
            "'$input' parsed only a prefix, stopping at '${tokens.LT(1)?.text}'",
        )
        return ctx
    }

    @Test
    fun constraintSetHoldsFactsAndRules() {
        val ctx =
            parseExpr(
                """
                #{
                    Edge("a", "b").
                    Path(x, z) :- Path(x, y), Edge(y, z).
                }
                """.trimIndent(),
            )
        assertNull(ctx.exception)
    }

    @Test
    fun constraintBodySupportsNegationFixGuardsAndFunctionals() {
        val ctx =
            parseExpr(
                """
                #{
                    Reach(x) :- not Blocked(x), fix Seed(x), if (x > 0).
                    Pair(x, y) :- let (a, b) = f(x), Edge(a, b).
                }
                """.trimIndent(),
            )
        assertNull(ctx.exception)
    }

    @Test
    fun fixpointKeywordsShareOneExpressionList() {
        for (
        source in
        listOf(
            "solve p, q project Path, Edge",
            "psolve p, q",
            "query p, q select (x, y) from Path(x, y) where x > 0",
            "inject xs, ys into Path/2, Edge/2",
        )
        ) {
            assertNull(parseExpr(source).exception, "should parse: $source")
        }
    }

    @Test
    fun effectAndHandlerForms() {
        // `do op(...)` and `try ... with` were removed from Flix. The surviving forms are
        // `try e catch { ... }`, `run e with handler E { ... }` and `throw e`.
        assertNull(parseExpr("try e catch { case ex: Exception => () }").exception)
        assertNull(parseExpr("run e with handler Logger { def log(msg) = () }").exception)
        assertNull(parseExpr("throw e").exception)
        assertNull(parseExpr("unsafe IO { e }").exception)
    }

    @Test
    fun concurrencyForms() {
        assertNull(parseExpr("spawn e @ rc").exception)
        assertNull(parseExpr("select { case x <- Channel.recv(c) => x, case _ => 0 }").exception)
        assertNull(parseExpr("par (x <- a; y <- b) yield x + y").exception)
        assertNull(parseExpr("region rc { e }").exception)
    }

    @Test
    fun comprehensionForms() {
        assertNull(parseExpr("foreach (x <- xs; if p(x)) body").exception)
        assertNull(parseExpr("forM (x <- xs; let y = f(x)) yield y").exception)
        assertNull(parseExpr("forA (x <- xs) yield x").exception)
    }

    @Test
    fun constraintTerminatorIsDistinctFromQualifiedNameSeparator() {
        // `Q.R` is one qualified name; the trailing dot ends the constraint because it is
        // followed by whitespace, which the lexer tokenizes differently.
        val parser =
            FlixParser(CommonTokenStream(FlixLexer(CharStreams.fromString("#{ P(x) :- Q.R(x). }"))))
        assertNull(parser.expr().exception)
        assertEquals(0, parser.numberOfSyntaxErrors)
    }
}
