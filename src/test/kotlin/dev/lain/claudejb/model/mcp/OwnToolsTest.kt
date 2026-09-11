package dev.lain.claudejb.model.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OwnToolsTest {

    private fun input(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `a call to one of our servers is recognised and labelled by server and tool`() {
        val run = OwnTools.parse("mcp__code__run", input("""{"tool":"find_symbols","args":{"query":"Mcp"}}"""))!!
        assertEquals("code ▸ find_symbols", OwnTools.label(run))
        assertEquals("query: Mcp", OwnTools.argsToon(input("""{"tool":"find_symbols","args":{"query":"Mcp"}}""")))
        assertEquals("ops ▸ tools(services)", OwnTools.label(OwnTools.parse("mcp__ops__tools", input("""{"domain":"services"}"""))!!))
        assertEquals("vcs ▸ domains", OwnTools.label(OwnTools.parse("mcp__vcs__domains", input("{}"))!!))
        assertTrue(OwnTools.isOwn("mcp__run__run"))
        assertFalse(OwnTools.isOwn("mcp__jetbrains__get_file_text"))
        assertFalse(OwnTools.isOwn("Bash"))
        assertNull(OwnTools.argsToon(input("""{"tool":"x","args":{}}""")))
    }

    @Test
    fun `a result decodes from TOON to JSON for the card, comment primer included, and garbage decodes to nothing`() {
        val decoded = OwnTools.decodeResult("# TOON primer\ndomains[2]{name,description}:\n  read,Files\n  search,Text\n")!!.jsonObject
        assertEquals(2, decoded["domains"]!!.jsonArray.size)
        assertNull(OwnTools.decodeResult("name: \"unterminated"))
    }

    @Test
    fun `a call on a file carries its path in the title, an edit becomes a review of the native kind, a read its text`() {
        val edit = input("""{"tool":"replace_text","args":{"path":"src/A.kt","old_string":"a","new_string":"b"}}""")
        val call = OwnTools.parse("mcp__code__run", edit)!!
        assertEquals("code ▸ replace_text ▸ src/A.kt", OwnTools.label(call, edit))
        assertEquals("src/A.kt", OwnTools.path(edit))
        assertTrue(OwnTools.isEdit(call) && !OwnTools.isRead(call))
        val review = OwnTools.reviewAs(call, edit, "/home/u/proj")!!
        assertEquals("Edit", review.toolName)
        assertEquals("/home/u/proj/src/A.kt", review.input["file_path"]!!.jsonPrimitive.content)
        assertEquals("a", review.input["old_string"]!!.jsonPrimitive.content)
        assertNull(review.input["path"])

        val write = input("""{"tool":"write_file","args":{"path":"/abs/B.kt","content":"x"}}""")
        assertEquals("Write", OwnTools.reviewAs(OwnTools.parse("mcp__code__run", write)!!, write, "/home/u/proj")!!.toolName)
        assertEquals("/abs/B.kt", OwnTools.reviewAs(OwnTools.parse("mcp__code__run", write)!!, write, null)!!.input["file_path"]!!.jsonPrimitive.content)
        val insert = input("""{"tool":"insert_text","args":{"path":"C.kt","line":3,"content":"y"}}""")
        assertEquals("InsertText", OwnTools.reviewAs(OwnTools.parse("mcp__code__run", insert)!!, insert, "/p")!!.toolName)
        val inserted = OwnTools.reviewAs(OwnTools.parse("mcp__code__run", insert)!!, insert, "/p")!!.input
        assertEquals("a\nb\ny\nc\n", OwnTools.insertedText(inserted, "a\nb\nc\n"))

        val read = input("""{"tool":"read_file","args":{"path":"src/A.kt"}}""")
        val reading = OwnTools.parse("mcp__code__run", read)!!
        assertTrue(OwnTools.isRead(reading))
        assertNull(OwnTools.reviewAs(reading, read, "/p"))
        assertEquals("fun a()", OwnTools.readText(OwnTools.decodeResult("path: src/A.kt\nlines: 1\ntext: \"fun a()\"\n")))
        assertNull(OwnTools.readText(OwnTools.decodeResult("count: 0\n")))
    }
}
