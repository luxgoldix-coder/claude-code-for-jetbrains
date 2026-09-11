package dev.lain.claudejb.model.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ToolModelTest {

    @Test
    fun `a domain never exposes more than four tools`() {
        val tools = (1..5).map { Tool(ToolSpec("t$it", "tool $it")) { ToolResult("") } }
        assertThrows<IllegalArgumentException> { ToolDomain("big", "too many", tools) }
        ToolDomain("fits", "four", tools.take(ToolDomain.MAX_TOOLS))
    }

    @Test
    fun `tool and domain names are unique across the catalog`() {
        val a = ToolDomain("a", "", listOf(Tool(ToolSpec("same", "")) { ToolResult("") }))
        val b = ToolDomain("b", "", listOf(Tool(ToolSpec("same", "")) { ToolResult("") }))
        assertThrows<IllegalArgumentException> { ToolCatalog(listOf(a, b)) }
        assertThrows<IllegalArgumentException> { ToolCatalog(listOf(a, ToolDomain("a", "", emptyList()))) }
        assertEquals("same", ToolCatalog(listOf(a)).tool("same")?.spec?.name)
    }

    @Test
    fun `the input schema is strict and lists the required parameters`() {
        val schema = ToolSpec("x", "", listOf(Param("path", "where"), Param("limit", "how many", type = "integer", required = false))).inputSchema
        assertEquals("object", schema["type"]?.jsonPrimitive?.content)
        assertEquals("false", schema["additionalProperties"]?.jsonPrimitive?.content)
        assertEquals(listOf("path"), schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("integer", schema["properties"]!!.jsonObject["limit"]!!.jsonObject["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `arguments are read by name with typed errors`() {
        val args = ToolArgs(parse("""{"path":"a.kt","limit":"12","deep":"true","bad":"x"}"""))
        assertEquals("a.kt", args.string("path"))
        assertEquals(12, args.int("limit", 1))
        assertEquals(5, args.int("missing", 5))
        assertTrue(args.boolean("deep", false))
        assertThrows<ToolException> { args.string("missing") }
        assertThrows<ToolException> { args.int("bad", 0) }
        assertThrows<ToolException> { args.boolean("bad", true) }
    }

    @Test
    fun `errors are TOON too`() {
        val error = ToolResult.error("no such file")
        assertTrue(error.isError)
        assertEquals("error: no such file", error.text)
        assertFalse(ToolResult.toon(parse("""{"a":1}""")).isError)
    }

    @Test
    fun `the budget cuts on a line boundary and says so in a comment line`() {
        val budget = OutputBudget(120)
        val text = (1..40).joinToString("\n") { "line $it" }
        val fitted = budget.fit(text)
        assertTrue(fitted.length <= 120) { fitted }
        assertTrue(fitted.lines().last().startsWith("# truncated: ")) { fitted }
        assertTrue(fitted.lines().dropLast(1).all { it.startsWith("line ") }) { fitted }
        assertEquals("short", budget.fit("short"))
    }

    @Test
    fun `tokens rotate with an overlap window and compare in constant shape`() {
        var now = 0L
        val ring = TokenRing(overlapMillis = 100, clock = { now })
        val first = ring.token
        assertTrue(ring.accepts(first))
        assertFalse(ring.accepts(null))
        assertFalse(ring.accepts(first.dropLast(1) + "x"))
        val second = ring.rotate()
        assertTrue(ring.accepts(second))
        assertTrue(ring.accepts(first))
        now = 101
        assertFalse(ring.accepts(first))
        assertTrue(ring.accepts(second))
        assertTrue(first.length >= 43 && first != second)
    }

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
}
