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
 * **Rule nodes and native comment groups are emitted, never token leaves.** Comments are
 * reconstructed from the original token stream using native positions. This does not claim
 * token accounting: ordinary terminal leaves and hidden whitespace are deliberately absent.
 * Recovery and diagnostics capabilities remain those declared by the projection contract.
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

    internal data class Node(
        val kind: String,
        val start: Token,
        val stop: Token,
        val children: MutableList<Node> = mutableListOf(),
        val documentable: Boolean = false,
    )

    /** Native token ends count code points and newlines, never UTF-16 string length or EOF text. */
    private fun end(token: Token): Pair<Int, Int> {
        var line = token.line
        var col = token.charPositionInLine + 1
        if (token.type != Token.EOF) {
            token.text.orEmpty().codePoints().forEach {
                if (it == '\n'.code) {
                    line++
                    col = 1
                } else {
                    col++
                }
            }
        }
        return line to col
    }

    /**
     * The node kind the projection map is keyed by.
     *
     * A labelled alternative (`# AddExpr`) is a strict subclass of its rule's context class, and its
     * label is the distinction the map needs: `ruleIndex` is shared by every alternative of a rule, so
     * keying on it collapses all labelled alternatives of `expr` into the one string `expr`. An
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
     * `Expr.Binary` and `Type.Binary` are (lhs, `Operator`, rhs) and the unary kinds are
     * (`Operator`, operand), but the operator is a bare token here, and tokens are never emitted -- so without a synthetic node
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
            "UnaryType",
            "AndType",
            "OrType",
            "XorType",
            "EffectIntersectionType",
            "EffectSumType",
            "RvAndType",
            "RvAddSubType",
            "ArrowType",
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

    private fun node(
        ctx: ParserRuleContext,
        names: Array<String>,
    ): Node {
        val rule = names[ctx.ruleIndex]
        val kind =
            when {
                ctx is FlixParser.LiteralPrimaryContext || ctx is FlixParser.StringPrimaryContext -> "literalExpression"
                ctx is FlixParser.UnitPrimaryContext -> "tupleExpression"
                ctx is FlixParser.TupleTypeContext &&
                    (ctx.recordFieldOrType().isEmpty() || ctx.recordFieldOrType().first().EQUAL() != null)
                -> "recordRowType"
                ctx is FlixParser.VariableTypeContext -> "variableType"
                ctx is FlixParser.SchemaTypeContext -> "schemaType"
                ctx is FlixParser.SchemaRowTypeContext -> "schemaRowType"
                ctx is FlixParser.ExtensibleTypeContext -> "extensibleType"
                ctx is FlixParser.SchemaTermContext && ctx.typeArgs() == null -> "predicateTypes"
                ctx is FlixParser.SchemaTermContext -> "predicateAlias"
                ctx is FlixParser.DefDeclarationContext && ctx.REDEF() != null -> "redefDeclaration"
                ctx is FlixParser.DefDeclarationContext &&
                    ctx.parent is FlixParser.TraitMemberContext -> "signatureDeclaration"
                ctx is FlixParser.AssocTypeDeclarationContext &&
                    ctx.parent is FlixParser.InstanceMemberContext -> "associatedTypeDefinition"
                ctx is FlixParser.EnumDeclarationContext && ctx.RESTRICTABLE() != null -> "restrictableEnumDeclaration"
                else -> kindOf(ctx, names)
            }
        val documentable =
            rule in
                setOf(
                    "aliasDeclaration",
                    "assocTypeDeclaration",
                    "defDeclaration",
                    "effDeclaration",
                    "enumCase",
                    "enumDeclaration",
                    "instanceDeclaration",
                    "modDeclaration",
                    "opDeclaration",
                    "structDeclaration",
                    "structField",
                    "traitDeclaration",
                ) ||
                ctx is FlixParser.LocalDefExprContext
        // Parser2 consumes enum commas outside the Case node, before opening its documentable mark.
        val start =
            if (ctx is FlixParser.EnumCaseContext) {
                ctx.CASE()?.symbol ?: ctx.nameUppercase().start
            } else {
                ctx.start
            }
        val stop =
            if (ctx is FlixParser.EnumCaseContext) {
                ctx.RPAREN()?.symbol ?: ctx.nameUppercase().stop
            } else {
                ctx.stop
                    ?: start
            }
        return Node(kind, start, stop, documentable = documentable).also { n ->
            // These reference nodes contain tokens directly, which this projection omits.
            if (kind !in setOf("literalExpression", "variableType", "LiteralPrimary", "StringPrimary")) {
                var i = 0
                while (i < ctx.childCount) {
                    if (kind in operatorLabels && isOperatorPart(ctx, i)) {
                        var j = i
                        while (j + 1 < ctx.childCount && isOperatorPart(ctx, j + 1)) j++
                        val first = ctx.getChild(i)
                        val last = ctx.getChild(j)
                        val operatorStart =
                            if (first is TerminalNode) {
                                first.symbol
                            } else {
                                (first as ParserRuleContext)
                                    .start
                            }
                        val operatorStop =
                            if (last is TerminalNode) {
                                last.symbol
                            } else {
                                (last as ParserRuleContext).stop
                                    ?: operatorStart
                            }
                        val operator = Node(OPERATOR_KIND, operatorStart, operatorStop)
                        for (k in i..j) {
                            val part = ctx.getChild(k)
                            if (part is ParserRuleContext && part !is FlixParser.NameMathContext) {
                                operator.children += node(part, names)
                            }
                        }
                        n.children += operator
                        i = j + 1
                    } else {
                        val child = ctx.getChild(i)
                        if (child is ParserRuleContext) n.children += node(child, names)
                        i++
                    }
                }
            }
            if (ctx is FlixParser.SchemaTermContext && ctx.SEMI() != null && n.children.isNotEmpty()) {
                val term = n.children.removeAt(n.children.lastIndex)
                n.children += Node("latticeTerm", term.start, term.stop, mutableListOf(term))
            }
            val payload =
                when (ctx) {
                    is FlixParser.EnumCaseContext -> ctx.LPAREN()?.symbol to ctx.RPAREN()?.symbol
                    is FlixParser.EnumDeclarationTailContext -> ctx.LPAREN()?.symbol to ctx.RPAREN()?.symbol
                    else -> null to null
                }
            if (payload.first != null && payload.second != null) {
                val types =
                    n.children.filter {
                        it.start.tokenIndex > payload.first!!.tokenIndex &&
                            it.stop.tokenIndex < payload.second!!.tokenIndex
                    }
                val at = n.children.indexOfFirst { it in types }
                n.children.removeAll(types.toSet())
                n.children.add(
                    if (at < 0) n.children.size else at,
                    Node("caseBody", payload.first!!, payload.second!!, types.toMutableList()),
                )
            }
            val group =
                when (ctx) {
                    is FlixParser.UseClauseContext -> Triple("useMany", ctx.LBRACE()?.symbol, ctx.RBRACE()?.symbol)
                    is FlixParser.ImportClauseContext ->
                        Triple(
                            "importMany",
                            ctx.LBRACE()?.symbol,
                            ctx.RBRACE()?.symbol,
                        )
                    else -> null
                }
            if (group?.second != null && group.third != null) {
                val members = n.children.filter { it.kind == "useName" }
                val at = n.children.indexOfFirst { it.kind == "useName" }
                n.children.removeAll(members.toSet())
                n.children.add(
                    if (at < 0) n.children.size else at,
                    Node(group.first, group.second!!, group.third!!, members.toMutableList()),
                )
            }
            if (ctx is FlixParser.CompilationUnitContext) {
                val uses = n.children.filter { it.kind == "usesOrImports" }
                if (uses.isNotEmpty()) {
                    n.children.removeAll(uses.toSet())
                    n.children.add(0, Node("useList", uses.first().start, uses.last().stop, uses.toMutableList()))
                }
            }
        }
    }

    /** Reinsert native off-channel comment runs using Parser2.buildTree's next-token rule. */
    private fun placeComments(
        root: Node,
        tokens: List<Token>,
    ) {
        fun path(
            n: Node,
            index: Int,
        ): List<Node> {
            val child =
                n.children.firstOrNull {
                    it.start.tokenIndex <= index && index <= it.stop.tokenIndex
                }
            return listOf(n) + if (child == null) emptyList() else path(child, index)
        }

        fun group(
            kind: String,
            run: List<Token>,
        ): Node = Node(kind, run.first(), run.last())
        val comments = mutableListOf<Token>()
        var previous: Token? = null
        for (next in tokens) {
            if (next.channel == FlixLexer.COMMENTS || next.channel == FlixLexer.DOC_COMMENTS) {
                comments += next
                continue
            }
            if (next.channel != Token.DEFAULT_CHANNEL) continue
            if (comments.isNotEmpty()) {
                val chain = path(root, next.tokenIndex)
                val from = chain.indexOfFirst { it !== root && it.start.tokenIndex == next.tokenIndex }
                val owner = if (from < 0) null else chain.drop(from).firstOrNull { it.documentable }
                val plainOwner = if (from < 0) chain.last() else chain[from - 1]
                val lastLine = previous?.let { end(it).first } ?: 0
                val trailingCount = comments.takeWhile { it.line == lastLine }.size
                val firstDoc =
                    comments.indexOfFirst {
                        it.type == FlixLexer.DOC_COMMENT &&
                            comments.indexOf(it) >= trailingCount
                    }
                val docStart = if (owner == null || firstDoc < 0) comments.size else firstDoc
                if (docStart < comments.size) owner!!.children.add(0, group("docComment", comments.drop(docStart)))
                val plain =
                    listOf(comments.take(trailingCount), comments.subList(trailingCount, docStart))
                        .filter { it.isNotEmpty() }
                        .map { group("plainComment", it) }
                val before = plainOwner.children.indexOfFirst { it.start.tokenIndex >= next.tokenIndex }
                plainOwner.children.addAll(if (before < 0) plainOwner.children.size else before, plain)
                comments.clear()
            }
            previous = next
        }
    }

    private fun render(
        n: Node,
        sb: StringBuilder,
    ) {
        val (endLine, endCol) =
            if (n.stop.tokenIndex < n.start.tokenIndex) {
                n.start.line to n.start.charPositionInLine + 1
            } else {
                end(n.stop)
            }
        sb.append("{\"kind\":\"").append(esc(n.kind)).append("\"")
        sb.append(",\"span\":{\"start\":{\"line\":").append(if (n.kind == "compilationUnit") 1 else n.start.line)
        sb.append(",\"col\":").append(if (n.kind == "compilationUnit") 1 else n.start.charPositionInLine + 1)
        sb
            .append("},\"end\":{\"line\":")
            .append(endLine)
            .append(",\"col\":")
            .append(endCol)
            .append("}}")
        sb.append(",\"children\":[")
        n.children.forEachIndexed { i, child ->
            if (i > 0) sb.append(",")
            render(child, sb)
        }
        sb.append("]}")
    }

    /** Parses one file and returns its projected tree, or null when the parse produced no tree. */
    internal fun project(file: File): String? {
        val root = projectTree(file) ?: return null
        return StringBuilder().also { render(root, it) }.toString()
    }

    /** Keeps the structured projection available to source-level checks before serialization. */
    internal fun projectTree(file: File): Node? {
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
        val root = node(tree, parser.ruleNames)
        tokens.fill()
        placeComments(root, tokens.tokens)
        return root
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
