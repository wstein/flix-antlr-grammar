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

    @Test
    fun `emits no token leaves`() {
        // Token leaves would make source_invariants' token-accounting check evaluate a fiction:
        // comments live on the COMMENTS and DOC_COMMENTS channels and never enter the parse tree,
        // so concatenating what the tree holds would silently lose them.
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
}
