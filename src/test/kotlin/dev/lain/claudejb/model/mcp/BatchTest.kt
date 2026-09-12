package dev.lain.claudejb.model.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BatchTest {

    private val paths = Batch.Plural("paths", "path")
    private val edits = Batch.Plural("edits", "path", Items("object", listOf(Param("path", ""), Param("old_string", ""))))

    private fun args(json: String) = ToolArgs(Json.parseToJsonElement(json).jsonObject, "tu_1")

    private fun read(a: ToolArgs): JsonObject = buildJsonObject {
        put("path", a.string("path"))
        put("limit", a.int("limit", 400))
        if (a.string("path") == "missing.kt") throw ToolException("no such path: missing.kt")
        put("text", "fun ${a.string("path")}")
    }

    @Test
    fun `without the plural the single call runs untouched`() = runBlocking {
        val out = Batch.run(args("""{"path":"a.kt","limit":"5"}"""), paths, ::read)
        assertEquals("a.kt", out["path"]!!.jsonPrimitive.content)
        assertNull(out["items"])
    }

    @Test
    fun `a list of strings runs one call per item with the shared arguments, a failure is a row and the rest go on`() = runBlocking {
        val out = Batch.run(args("""{"paths":["a.kt","missing.kt","b.kt"],"limit":"7"}"""), paths, ::read)
        assertEquals(3, out["count"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, out["failed"]!!.jsonPrimitive.content.toInt())
        val items = out["items"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("path", "limit", "text"), items[0].keys.toList())
        assertEquals("7", items[0]["limit"]!!.jsonPrimitive.content)
        assertEquals("no such path: missing.kt", items[1]["error"]!!.jsonPrimitive.content)
        assertEquals("missing.kt", items[1]["path"]!!.jsonPrimitive.content)
        assertEquals("fun b.kt", items[2]["text"]!!.jsonPrimitive.content)
        val toolUseIds = Batch.expand(args("""{"paths":["a.kt"]}"""), paths)!!.map { it.toolUseId }
        assertEquals(listOf("tu_1"), toolUseIds)
    }

    @Test
    fun `a list of objects merges each item over the shared arguments`() = runBlocking {
        val out = Batch.run(args("""{"edits":[{"path":"a.kt","limit":"1"},{"path":"b.kt"}],"limit":"9"}"""), edits, ::read)
        val items = out["items"]!!.jsonArray.map { it.jsonObject }
        assertEquals("1", items[0]["limit"]!!.jsonPrimitive.content)
        assertEquals("9", items[1]["limit"]!!.jsonPrimitive.content)
    }

    @Test
    fun `singular and plural together, an empty list, a non-list, a bad item or too many items are typed errors`() {
        assertThrows<ToolException> { Batch.expand(args("""{"path":"a.kt","paths":["b.kt"]}"""), paths) }
        assertThrows<ToolException> { Batch.expand(args("""{"paths":[]}"""), paths) }
        assertThrows<ToolException> { Batch.expand(args("""{"paths":"a.kt"}"""), paths) }
        assertThrows<ToolException> { Batch.expand(args("""{"paths":[1]}"""), paths) }
        val many = (1..Batch.MAX_ITEMS + 1).joinToString(",") { "\"f$it.kt\"" }
        assertThrows<ToolException> { Batch.expand(args("""{"paths":[$many]}"""), paths) }
    }

    @Test
    fun `a run list refuses the keys that would make its items ambiguous, and the size of any list is known`() {
        assertThrows<ToolException> { Batch.expand(args("""{"job":"run-1","names":["a"]}"""), Batch.RUN_NAMES) }
        assertThrows<ToolException> { Batch.expand(args("""{"name":"Kotlin tests","paths":["a.kt"]}"""), Batch.RUN_PATHS) }
        assertEquals(2, Batch.expand(args("""{"paths":["a.kt","b.kt"],"wait":"5"}"""), Batch.RUN_PATHS)!!.size)
        assertEquals(3, Batch.size(args("""{"hashes":["a","b","c"]}""").json))
        assertEquals(1, Batch.size(args("""{"positions":[{"path":"a.kt","line":1}]}""").json))
        assertNull(Batch.size(args("""{"path":"a.kt"}""").json))
    }

    @Test
    fun `the plural parameter carries the shape of its items into the schema`() {
        val spec = ToolSpec("t", "", listOf(Param("path", "", required = false), Batch.param(edits, "many")))
        val property = spec.inputSchema["properties"]!!.jsonObject["edits"]!!.jsonObject
        assertEquals("array", property["type"]!!.jsonPrimitive.content)
        val items = property["items"]!!.jsonObject
        assertEquals("object", items["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("path", "old_string"), items["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf<String>(), spec.inputSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
    }
}
