package dev.lain.claudejb.model.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
}
