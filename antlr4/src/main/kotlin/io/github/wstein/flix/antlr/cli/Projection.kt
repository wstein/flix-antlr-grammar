package io.github.wstein.flix.antlr.cli

import io.github.wstein.flix.antlr.FlixLexer
import io.github.wstein.flix.antlr.FlixParser
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import java.io.File
import kotlin.system.exitProcess

/**
 * Emits this grammar's parse of every `flix-spec` fixture as a canonical projected tree.
 *
 * This is the consumer half of the conformance check. `flix-spec` owns the canonical `TreeKind`
 * vocabulary, the fixtures, and the comparison itself; this repository owns the grammar and
 * `conformance/projection-map.json`, which maps our rule names onto that vocabulary.
 *
 * **The comparison is deliberately not reimplemented here.** `flix-jetbrains-plugin` ported it into
 * Kotlin, and within a single release that port had drifted from the original in two ways that both
 * changed its results -- it applied transparency level-wise instead of as a bottom-up fixed point,
 * and it did not know about recovery markers. "The comparison lives in one place so four
 * repositories do not re-derive it four times" is `flix-spec`'s reason to exist, so this writes
 * trees and shells out, exactly as `tree-sitter-flix` does.
 *
 * **Only rule nodes are emitted, never token leaves.** ANTLR has the real token text, so emitting it
 * looks like a free upgrade -- it would let `token-accounting`, the one oracle-free check in the
 * suite, actually run. It would also be wrong: this grammar routes comments to the `COMMENTS` and
 * `DOC_COMMENTS` channels, and off-channel tokens never appear in the parse tree, so concatenating
 * what the tree holds would silently omit every comment and *fail* a check that should have stood
 * down. Standing down honestly beats evaluating a fiction. Splicing the off-channel tokens back into
 * position is the upgrade path, and it is a real piece of work rather than a flag.
 *
 * Usage:
 *   projectFixtures --flix-spec <dir> [--out <dir>]
 */
object Projection {
    /** Escapes a string for a JSON string literal. */
    internal fun esc(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * The node kind the projection map is keyed by.
     *
     * A labelled alternative (`# AddExpr`) is a strict subclass of its rule's context class, and its
     * label is the distinction the map needs: `ruleIndex` is shared by every alternative of a rule, so
     * keying on it collapses all 49 labelled alternatives of `expr` into the one string `expr`. An
     * unlabelled rule's context extends [ParserRuleContext] directly and keeps its camelCase rule
     * name, which is what every existing key in the map is.
     */
    internal fun kindOf(
        ctx: ParserRuleContext,
        ruleNames: Array<String>,
    ): String =
        if (ctx.javaClass.superclass != ParserRuleContext::class.java) {
            ctx.javaClass.simpleName.removeSuffix("Context")
        } else {
            ruleNames[ctx.ruleIndex]
        }

    /**
     * Labels whose operator the reference wraps in an `Operator` node.
     *
     * `Expr.Binary` is (lhs, `Operator`, rhs) and `Expr.Unary` is (`Operator`, operand), but the
     * operator is a bare token here, and tokens are never emitted -- so without a synthetic node
     * every binary would project with two children against the reference's three. Synthesising it
     * in the projection keeps the grammar's precedence levels untouched: a shared `binaryOp` rule
     * would have flattened them.
     */
    private val operatorLabels =
        setOf(
            "NotExpr",
            "SignExpr",
            "LazyForceExpr",
            "DiscardExpr",
            "UserOpExpr",
            "InfixCallExpr",
            "AngledPlusExpr",
            "MultExpr",
            "AddExpr",
            "ConsExpr",
            "CompareExpr",
            "EqualityExpr",
            "AndExpr",
            "OrExpr",
            "InstanceOfExpr",
        )

    /** The native kind of a synthesised operator node; the map sends it to `Operator`. */
    internal const val OPERATOR_KIND = "operator"

    /**
     * Whether [child] is part of an operator spelling rather than an operand.
     *
     * A token always is. So is `nameMath` in `UserOpExpr`, which the reference holds as a
     * `NameMath` token inside the `Operator`, not as an `Ident`. A rule child between two tokens --
     * the name in `` a `f` b `` -- is part of the operator too, which is where the reference puts
     * its `Ident`. A rule child after the last operator token, as in `x instanceof T`, is an operand.
     */
    private fun isOperatorPart(
        ctx: ParserRuleContext,
        i: Int,
    ): Boolean {
        val child = ctx.getChild(i)
        return child is TerminalNode ||
            child is FlixParser.NameMathContext ||
            (
                child is ParserRuleContext &&
                    child !is FlixParser.ExprContext &&
                    i > 0 &&
                    ctx.getChild(i - 1) is TerminalNode &&
                    i + 1 < ctx.childCount &&
                    ctx.getChild(i + 1) is TerminalNode
            )
    }

    private fun appendSpan(
        sb: StringBuilder,
        start: Token?,
        stop: Token?,
    ) {
        if (start == null || stop == null) return
        sb.append(",\"span\":{\"start\":{\"line\":").append(start.line)
        sb.append(",\"col\":").append(start.charPositionInLine + 1)
        sb.append("},\"end\":{\"line\":").append(stop.line)
        // ANTLR reports the stop token's *start* column; the end column is one past its text.
        // EOF is zero-width. Its `text` is the literal string "<EOF>", so measuring the token
        // the way every other token is measured pushes the root's end column five characters
        // past the end of the file -- on every fixture, since every parse ends at EOF.
        val width = if (stop.type == Token.EOF) 0 else (stop.text?.length ?: 0)
        sb.append(",\"col\":").append(stop.charPositionInLine + 1 + width)
        sb.append("}}")
    }

    private fun firstToken(tree: ParseTree): Token? =
        when (tree) {
            is TerminalNode -> tree.symbol
            is ParserRuleContext -> tree.start
            else -> null
        }

    private fun lastToken(tree: ParseTree): Token? =
        when (tree) {
            is TerminalNode -> tree.symbol
            is ParserRuleContext -> tree.stop
            else -> null
        }

    /**
     * Renders one rule node and its rule children.
     *
     * `span` is advisory and not compared, but it is emitted because ANTLR has it exactly and a
     * projected tree that carries positions is far easier to debug against the reference's own.
     */
    private fun render(
        ctx: ParserRuleContext,
        ruleNames: Array<String>,
        sb: StringBuilder,
    ) {
        val kind = kindOf(ctx, ruleNames)
        sb.append("{\"kind\":\"").append(esc(kind)).append("\"")
        appendSpan(sb, ctx.start, ctx.stop)

        sb.append(",\"children\":[")
        var first = true
        val synthesiseOperator = kind in operatorLabels
        var i = 0
        while (i < ctx.childCount) {
            if (synthesiseOperator && isOperatorPart(ctx, i)) {
                var j = i
                while (j + 1 < ctx.childCount && isOperatorPart(ctx, j + 1)) j++
                if (!first) sb.append(",")
                first = false
                sb.append("{\"kind\":\"").append(OPERATOR_KIND).append("\"")
                appendSpan(sb, firstToken(ctx.getChild(i)), lastToken(ctx.getChild(j)))
                sb.append(",\"children\":[")
                var innerFirst = true
                for (k in i..j) {
                    val part = ctx.getChild(k)
                    if (part is ParserRuleContext && part !is FlixParser.NameMathContext) {
                        if (!innerFirst) sb.append(",")
                        innerFirst = false
                        render(part, ruleNames, sb)
                    }
                }
                sb.append("]}")
                i = j + 1
                continue
            }
            val child = ctx.getChild(i)
            if (child is ParserRuleContext) {
                if (!first) sb.append(",")
                first = false
                render(child, ruleNames, sb)
            }
            i++
        }
        sb.append("]}")
    }

    /** Parses one file and returns its projected tree, or null when the parse produced no tree. */
    internal fun project(file: File): String? {
        val stream = CharStreams.fromPath(file.toPath())
        val lexer = FlixLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = FlixParser(tokens)

        // Silence, not suppression: the negative fixtures are *supposed* to produce errors, and a
        // console full of them would bury the one line that matters. The recovered tree is the
        // measurement, and flix-spec's recovery lane is where its shape is judged.
        val quiet =
            object : BaseErrorListener() {
                override fun syntaxError(
                    recognizer: Recognizer<*, *>?,
                    offendingSymbol: Any?,
                    line: Int,
                    charPositionInLine: Int,
                    msg: String?,
                    e: RecognitionException?,
                ) = Unit
            }
        lexer.removeErrorListeners()
        lexer.addErrorListener(quiet)
        parser.removeErrorListeners()
        parser.addErrorListener(quiet)

        val tree = parser.compilationUnit() ?: return null
        val sb = StringBuilder()
        render(tree, parser.ruleNames, sb)
        return sb.toString()
    }

    /**
     * Wraps one projected tree in a projection document.
     *
     * `form` is `raw`: our own tree, our own rule names, nothing normalized away. The comparison
     * applies our projection map's transparency rules itself, and needs the unreduced tree because
     * two of its three lanes reduce it differently.
     */
    internal fun document(
        source: String,
        tree: String,
    ): String =
        buildString {
            append("{\n")
            append("  \"schemaVersion\": 2,\n")
            append("  \"generatedBy\": \"io.github.wstein.flix.antlr.cli.Projection\",\n")
            append("  \"form\": \"raw\",\n")
            append("  \"units\": [\n")
            append("    {\n")
            append("      \"source\": \"").append(esc(source)).append("\",\n")
            append("      \"diagnostics\": [],\n")
            append("      \"tree\": ").append(tree).append("\n")
            append("    }\n")
            append("  ]\n")
            append("}\n")
        }

    /**
     * The body of [main], returning an exit code instead of taking one.
     *
     * Split the way `Main.runValidation` is, and for the same reason: `exitProcess` cannot be
     * observed from a test, so a main that calls it directly is a main nothing can exercise.
     */
    internal fun run(args: Array<String>): Int {
        fun option(name: String): String? {
            val i = args.indexOf(name)
            return if (i >= 0 && i + 1 < args.size) args[i + 1] else null
        }

        val specPath = option("--flix-spec") ?: System.getenv("FLIX_SPEC")
        if (specPath == null) {
            System.err.println("This needs a checkout of flix-spec, which owns the fixtures.")
            System.err.println("  --flix-spec <path>      or      FLIX_SPEC=<path>")
            System.err.println("Clone it from https://github.com/wstein/flix-spec")
            return 2
        }
        val specDir = File(specPath)
        if (!specDir.isDirectory) {
            System.err.println("Not a directory: $specPath")
            return 2
        }

        val outDir = File(option("--out") ?: "build/flix-spec-projection")
        outDir.mkdirs()

        val fixtures =
            listOf("positive", "negative")
                .map { File(specDir, "fixtures/$it") }
                .filter { it.isDirectory }
                .flatMap { it.listFiles { f: File -> f.extension == "flix" }?.toList() ?: emptyList() }
                .sortedBy { it.name }

        if (fixtures.isEmpty()) {
            System.err.println("No fixtures under $specDir/fixtures/{positive,negative}/")
            return 1
        }

        var written = 0
        val skipped = mutableListOf<String>()

        for (fixture in fixtures) {
            val tree = project(fixture)
            if (tree == null) {
                skipped += fixture.name
                continue
            }
            // flix-spec-relative, because the comparison matches units by `source` and the
            // source-invariants lane resolves it from the flix-spec root.
            val relative = fixture.relativeTo(specDir).path.replace(File.separatorChar, '/')
            File(outDir, "${fixture.nameWithoutExtension}.json").writeText(document(relative, tree))
            written++
        }

        println("projected $written/${fixtures.size} fixtures into $outDir")
        // An adapter that silently drops what it cannot read always flatters its own grammar, so
        // every skip is reported and every skip fails the run.
        if (skipped.isNotEmpty()) {
            skipped.forEach { System.err.println("  SKIPPED $it") }
            System.err.println("error: ${skipped.size} fixture(s) produced no tree")
            return 1
        }
        return 0
    }

    @JvmStatic
    fun main(args: Array<String>): Unit = exitProcess(run(args))
}
