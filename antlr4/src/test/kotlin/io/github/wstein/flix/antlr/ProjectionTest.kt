package io.github.wstein.flix.antlr

import io.github.wstein.flix.antlr.cli.Projection
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers the projection that `flix-spec`'s conformance comparison reads.
 *
 * The properties asserted here are the ones a downstream comparison silently depends on, and each
 * of them fails in a way that looks like a grammar problem rather than an adapter problem:
 *
 *  - only rule nodes are emitted, never token leaves, because this grammar routes comments off the
 *    default channel and a tree-derived token stream would omit them;
 *  - `source` is flix-spec-relative, because the comparison matches units by it;
 *  - malformed input still yields a tree, because the negative fixtures are the whole point of the
 *    recovery lane and an adapter that returns nothing for them flatters its own grammar.
 */
class ProjectionTest {
    private fun write(
        name: String,
        text: String,
    ): File {
        val dir = createTempDirectory("projection-test").toFile()
        val file = File(dir, name)
        file.writeText(text)
        return file
    }

    @Test
    fun `projects a well-formed file into rule nodes`() {
        val file = write("hello.flix", "def main(): Unit \\ IO = println(\"hello\")\n")
        val tree = Projection.project(file)

        assertNotNull(tree, "a well-formed file must project")
        assertTrue(tree.startsWith("{\"kind\":\"compilationUnit\""), "the root is the start rule: $tree")
        assertTrue(tree.contains("\"kind\":\"defDeclaration\""), "the definition must appear: $tree")
    }

    private fun descendants(node: Projection.Node): List<Projection.Node> =
        listOf(node) + node.children.flatMap(::descendants)

    private fun projected(source: String): Projection.Node =
        assertNotNull(Projection.projectTree(write("structure.flix", source)))

    private fun find(
        node: Projection.Node,
        kind: String,
    ): Projection.Node = assertNotNull(descendants(node).find { it.kind == kind }, "$kind missing from $node")

    @Test
    fun `labelled alternatives are keyed by label, unlabelled rules by rule name`() {
        val root = projected("def f(x: Int32): Int32 = x + 1")
        val declaration =
            root.children
                .single()
                .children
                .single()
        assertEquals("defDeclaration", declaration.kind)
        assertEquals("AddExpr", find(declaration, "statement").children.single().kind)
    }

    @Test
    fun `a binary expression carries a synthesised operator between its operands`() {
        val add = find(projected("def f(x: Int32): Int32 = x + 1"), "AddExpr")
        assertEquals(listOf("PrimaryExpression", "operator", "PrimaryExpression"), add.children.map { it.kind })
        assertEquals(listOf("x", "+", "1"), add.children.map { it.start.text })
        assertEquals("+", add.children[1].stop.text)
        assertTrue(add.children[1].children.isEmpty())
    }

    @Test
    fun `a math-name operator is the operator, not an operand name`() {
        val source = "def f(a: Int32, b: Int32): Bool = a ⊆ b"
        val binary = find(projected(source), "UserOpExpr")
        assertEquals(listOf("PrimaryExpression", "operator", "PrimaryExpression"), binary.children.map { it.kind })
        val operator = binary.children[1]
        assertEquals("⊆", operator.start.text)
        assertEquals(source.indexOf("⊆"), operator.start.charPositionInLine)
        assertTrue(operator.children.isEmpty())
        assertFalse(descendants(binary).any { it.kind == "nameMath" })
    }

    @Test
    fun `backtick infix operator retains its qualified identifier`() {
        val source = "def f(): Int32 = a `Ops.combine` b"
        val binary = find(projected(source), "InfixCallExpr")
        assertEquals(listOf("PrimaryExpression", "operator", "PrimaryExpression"), binary.children.map { it.kind })
        val operator = binary.children[1]
        assertEquals("`", operator.start.text)
        assertEquals("`", operator.stop.text)
        assertEquals(source.indexOf("`"), operator.start.charPositionInLine)
        val name = operator.children.single()
        assertEquals("qname", name.kind)
        assertEquals(listOf("nameUppercase", "dot", "nameLowercase"), name.children.map { it.kind })
        assertEquals(listOf("Ops", ".", "combine"), name.children.map { it.start.text })
    }

    @Test
    fun `the root span ends at EOF rather than its displayed token text`() {
        val tree = assertNotNull(Projection.project(write("eof.flix", "def f(): Unit = ()\n")))
        assertTrue(
            tree.startsWith(
                "{\"kind\":\"compilationUnit\",\"span\":{\"start\":{\"line\":1,\"col\":1},\"end\":{\"line\":2,\"col\":1}}",
            ),
            tree,
        )
    }

    @Test
    fun `emits no token leaves`() {
        // Comment groups do not turn this rule-only projection into a token stream.
        // Ordinary terminals and hidden whitespace remain absent, so token accounting is N/A.
        val file = write("commented.flix", "/// doc\n// line\ndef f(): Unit = ()\n")
        val tree = assertNotNull(Projection.project(file))

        assertFalse(tree.contains("\"token\""), "no token leaves may be emitted: $tree")
        assertFalse(tree.contains("\"text\""), "no token text may be emitted: $tree")
    }

    @Test
    fun `every node carries a kind and a children array`() {
        val file = write("shape.flix", "def f(x: Int32): Int32 = x\n")
        val tree = assertNotNull(Projection.project(file))

        // One "children" per "kind": every emitted node is a well-formed projected node.
        val kinds = Regex("\"kind\":").findAll(tree).count()
        val children = Regex("\"children\":").findAll(tree).count()
        assertEquals(kinds, children, "every node needs a children array: $tree")
        assertTrue(kinds > 3, "expected a non-trivial tree, got $kinds nodes")
    }

    @Test
    fun `malformed input still projects a tree`() {
        // ANTLR recovers rather than giving up, and the negative fixtures depend on that: an
        // adapter that drops what it cannot parse reports only the inputs its grammar handles.
        val file = write("broken.flix", "def f(: Unit = (\n")
        val tree = Projection.project(file)

        assertNotNull(tree, "a recovered parse must still project")
        assertTrue(tree.startsWith("{\"kind\":\"compilationUnit\""), "root survives recovery: $tree")
    }

    @Test
    fun `the document declares its form and a relative source`() {
        val doc = Projection.document("fixtures/positive/hello.flix", "{\"kind\":\"compilationUnit\",\"children\":[]}")

        assertTrue(doc.contains("\"schemaVersion\": 2"), doc)
        assertTrue(doc.contains("\"form\": \"raw\""), "the comparison needs the unreduced tree: $doc")
        assertTrue(doc.contains("\"source\": \"fixtures/positive/hello.flix\""), doc)
        assertTrue(doc.contains("\"diagnostics\": []"), doc)
        assertFalse(doc.contains(File.separator + "Users"), "no absolute path may leak into a document: $doc")
    }

    @Test
    fun `escapes what would otherwise break the document`() {
        assertEquals("a\\\"b", Projection.esc("a\"b"))
        assertEquals("a\\\\b", Projection.esc("a\\b"))
        assertEquals("a\\nb", Projection.esc("a\nb"))
        assertEquals("a\\tb", Projection.esc("a\tb"))
        // A raw control character inside a JSON string is a parse error, not a formatting wrinkle.
        assertEquals("a\\u0001b", Projection.esc("ab"))
    }

    @Test
    fun `run projects a fixture tree and reports where it wrote`() {
        val spec = createTempDirectory("flix-spec-stub").toFile()
        File(spec, "fixtures/positive").mkdirs()
        File(spec, "fixtures/negative").mkdirs()
        File(spec, "fixtures/positive/ok.flix").writeText("def f(): Unit = ()\n")
        File(spec, "fixtures/negative/bad.flix").writeText("def f(: Unit = (\n")
        val out = createTempDirectory("projection-out").toFile()

        val code = Projection.run(arrayOf("--flix-spec", spec.path, "--out", out.path))

        assertEquals(0, code, "both fixtures project, so the run succeeds")
        val written = out.listFiles()?.map { it.name }?.sorted() ?: emptyList()
        assertEquals(listOf("bad.json", "ok.json"), written)
        // The source has to be flix-spec-relative or the comparison cannot match units by it.
        assertTrue(File(out, "ok.json").readText().contains("\"source\": \"fixtures/positive/ok.flix\""))
    }

    @Test
    fun `run refuses to guess where flix-spec is`() {
        // No default, deliberately: a hardcoded fallback works on exactly one machine.
        assertEquals(2, Projection.run(arrayOf("--flix-spec", "/no/such/directory")))
    }

    @Test
    fun `run reports an empty fixture set rather than succeeding on nothing`() {
        val spec = createTempDirectory("flix-spec-empty").toFile()
        File(spec, "fixtures/positive").mkdirs()
        assertEquals(
            1,
            Projection.run(arrayOf("--flix-spec", spec.path, "--out", createTempDirectory("o").toFile().path)),
        )
    }

    @Test
    fun `spans are one-indexed and end after the last character`() {
        val file = write("span.flix", "def f(): Unit = ()\n")
        val tree = assertNotNull(Projection.project(file))

        // flix-spec's schema requires line and col >= 1; a zero would fail validation there rather
        // than here, which is exactly the kind of late failure this asserts away.
        assertTrue(tree.contains("\"span\":{\"start\":{\"line\":1,\"col\":1}"), tree)
        assertFalse(Regex("\"col\":0").containsMatchIn(tree), "columns are one-indexed: $tree")
    }

    @Test
    fun `documentation belongs to the following declaration and plain comments survive`() {
        val tree =
            assertNotNull(Projection.project(write("docs.flix", "// lead\n/// docs\ndef f(): Int32 = 1 // trailing\n")))
        assertEquals(2, Regex("\"kind\":\"plainComment\"").findAll(tree).count(), tree)
        assertEquals(1, Regex("\"kind\":\"docComment\"").findAll(tree).count(), tree)
        assertTrue(tree.contains("\"children\":[{\"kind\":\"docComment\""), tree)
    }

    @Test
    fun `multiline token spans end at the native final line`() {
        val tree = assertNotNull(Projection.project(write("multiline.flix", "/* first\nsecond */\ndef f(): Unit = ()")))
        assertTrue(tree.contains("\"end\":{\"line\":2,\"col\":10}"), tree)
    }

    @Test
    fun `inline and orphan docs remain plain comments`() {
        val tree =
            assertNotNull(Projection.project(write("misplaced.flix", "def f(): Unit = () /// inline\n/// orphan\n")))
        assertEquals(2, Regex("\"kind\":\"plainComment\"").findAll(tree).count(), tree)
        assertFalse(tree.contains("\"kind\":\"docComment\""), tree)
    }

    @Test
    fun `CRLF documentation attaches to both outer and local definitions`() {
        val source = "/// outer\r\n@Deprecated\r\npub def f(): Unit = {\r\n/// local\r\ndef g(): Unit = ();\r\ng()\r\n}"
        val tree = assertNotNull(Projection.project(write("local.flix", source)))
        assertEquals(2, Regex("\"kind\":\"docComment\"").findAll(tree).count(), tree)
        assertTrue(tree.contains("\"end\":{\"line\":7,\"col\":2}"), tree)
    }

    @Test
    fun `projection retains concrete scalar literals type variables and schema forms`() {
        val source =
            """
            def f(x: a): a = 1
            type alias S[r] = #{A(Int32) | r}
            type alias R[r] = #(A(Int32) | r)
            type alias E[r] = #|A(Int32) | r|#
            """.trimIndent()
        val tree = assertNotNull(Projection.project(write("concrete.flix", source)))
        for (kind in listOf(
            "literalExpression",
            "variableType",
            "schemaType",
            "schemaRowType",
            "extensibleType",
            "predicateTypes",
        )) {
            assertTrue(tree.contains("\"kind\":\"$kind\""), "$kind missing: $tree")
        }
    }

    @Test
    fun `projection groups uses and distinguishes declaration roles`() {
        val source =
            """
            use A.f
            use B.g
            use pkg::{Game, Board}
            import java.lang.{String, Integer}
            trait T[a] { def f(x: a): a }
            instance T[Int32] { redef f(x: Int32): Int32 = x }
            restrictable enum E[s] { case A }
            """.trimIndent()
        val tree = assertNotNull(Projection.project(write("roles.flix", source)))
        for (kind in listOf(
            "useList",
            "useMany",
            "importMany",
            "signatureDeclaration",
            "redefDeclaration",
            "restrictableEnumDeclaration",
        )) {
            assertTrue(tree.contains("\"kind\":\"$kind\""), "$kind missing: $tree")
        }
    }

    @Test
    fun `enum payloads retain case body grouping`() {
        val source = "enum E { case A(Int32) case B(Int32, Int32) }\nenum S(Int32)"
        val tree = assertNotNull(Projection.project(write("cases.flix", source)))
        assertEquals(3, Regex("\"kind\":\"caseBody\"").findAll(tree).count(), tree)
    }

    @Test
    fun `documentation after a leading enum comma belongs to the case`() {
        val source = "enum E { ,\n/// documents A\ncase A }"
        val tree = assertNotNull(Projection.project(write("case-doc.flix", source)))
        assertEquals(1, Regex("\"kind\":\"docComment\"").findAll(tree).count(), tree)
    }
}
