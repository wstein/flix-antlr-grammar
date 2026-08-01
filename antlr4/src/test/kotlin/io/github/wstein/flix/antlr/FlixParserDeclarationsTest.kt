package io.github.wstein.flix.antlr

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FlixParserDeclarationsTest {
    private fun parse(input: String): FlixParser.CompilationUnitContext {
        val stream = CharStreams.fromString(input)
        val lexer = FlixLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = FlixParser(tokens)
        return parser.compilationUnit()
    }

    @Test
    fun testModuleAndDefDeclaration() {
        val input =
            """
            mod Math {
                pub def add(x: Int32, y: Int32): Int32 = x
            }
            """.trimIndent()

        val cu = parse(input)
        assertNull(cu.exception)
        assertEquals(1, cu.declaration().size)

        val mod = cu.declaration(0).modDeclaration()
        assertNotNull(mod)
        assertEquals("Math", mod.qname().text)
        assertEquals(1, mod.declaration().size)

        val def = mod.declaration(0).defDeclaration()
        assertNotNull(def)
        assertEquals("add", def.definitionName().text)
    }

    @Test
    fun testUseClauses() {
        val input =
            """
            use Bool.{==>, <==>}
            use List.map
            """.trimIndent()

        val cu = parse(input)
        assertNull(cu.exception)
        assertEquals(2, cu.usesOrImports().size)

        val use1 = cu.usesOrImports(0).useClause()
        assertEquals("Bool", use1.qname().text)
        assertEquals(2, use1.useName().size)

        val use2 = cu.usesOrImports(1).useClause()
        assertEquals("List.map", use2.qname().text)
    }

    @Test
    fun testEnumDeclaration() {
        val input =
            """
            enum Color {
                case Red,
                case Green,
                case Blue
            }
            """.trimIndent()

        val cu = parse(input)
        assertNull(cu.exception)
        assertEquals(1, cu.declaration().size)

        val enumDecl = cu.declaration(0).enumDeclaration()
        assertNotNull(enumDecl)
        assertEquals("Color", enumDecl.nameUppercase().text)
    }

    @Test
    fun testStructDeclaration() {
        val input =
            """
            struct Person {
                name: String,
                age: Int32
            }
            """.trimIndent()

        val cu = parse(input)
        assertNull(cu.exception)
        assertEquals(1, cu.declaration().size)

        val structDecl = cu.declaration(0).structDeclaration()
        assertNotNull(structDecl)
        assertEquals("Person", structDecl.nameUppercase().text)
    }

    @Test
    fun testAliasDeclaration() {
        val input = "type alias StringMap[v] = Map[String, v]"
        val cu = parse(input)
        assertNull(cu.exception)
        assertEquals(1, cu.declaration().size)

        val alias = cu.declaration(0).aliasDeclaration()
        assertNotNull(alias)
        assertEquals("StringMap", alias.nameUppercase().text)
    }

    @Test
    fun testClassAndInstanceDeclaration() {
        val input =
            """
            trait Functor[m] {
                pub def map(f: a -> b, x: m[a]): m[b]
            }

            instance Functor[Option] {
            }
            """.trimIndent()

        val cu = parse(input)
        assertNull(cu.exception)
        assertEquals(2, cu.declaration().size)

        val traitDecl = cu.declaration(0).traitDeclaration()
        assertNotNull(traitDecl)
        assertEquals("Functor", traitDecl.nameUppercase().text)

        val instDecl = cu.declaration(1).instanceDeclaration()
        assertNotNull(instDecl)
        assertEquals("Functor", instDecl.qname().text)
    }

    @Test
    fun testEffectDeclaration() {
        val input =
            """
            eff Logger {
                def log(msg: String): Unit
            }
            """.trimIndent()

        val cu = parse(input)
        assertNull(cu.exception)
        assertEquals(1, cu.declaration().size)

        val eff = cu.declaration(0).effDeclaration()
        assertNotNull(eff)
        assertEquals("Logger", eff.nameUppercase().text)
    }
}
