package dev.lain.claudejb.model.session.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdeMcpPromptTest {

    private val all = IdeRule.entries.toSet()
    private val servers = IdeServer.entries.toSet()

    @Test
    fun `no server means no block, and rules of a server that is off are left out`() {
        assertEquals("", IdeMcpPrompt.text(all, emptySet()))
        val onlyIndex = IdeMcpPrompt.text(all, setOf(IdeServer.INDEX))
        assertTrue(onlyIndex.contains("ide_read_file"))
        assertFalse(onlyIndex.contains("start_debug_session"))
        assertFalse(onlyIndex.contains("apply_patch"))
        assertTrue(onlyIndex.contains("Always:"))
    }

    @Test
    fun `every rule names at least one of its tools and reads as an order, not a preference`() {
        val text = IdeMcpPrompt.text(all, servers)
        val lines = text.lines().filter { it.substringBefore('.').toIntOrNull() != null }
        assertEquals(IdeRule.entries.size, lines.size)
        IdeRule.entries.filter { it.tools.isNotEmpty() }.forEach { rule ->
            assertTrue(lines.any { line -> rule.tools.any { line.contains(it) } }, rule.key)
        }
        listOf("subagent", ".claudetools", "fallback", "defect").forEach { assertTrue(text.contains(it), it) }
        listOf("prefer", "try to", "if possible", "consider", "when possible").forEach {
            assertFalse(text.lowercase().contains(it), it)
        }
        assertTrue(text.startsWith(IdeMcpPrompt.OPEN) && text.endsWith(IdeMcpPrompt.CLOSE))
    }

    @Test
    fun `the whole block with everything on stays under the token budget`() {
        val text = IdeMcpPrompt.text(all, servers)
        assertTrue(text.length < BUDGET_CHARS, "length=${text.length}")
    }

    @Test
    fun `only tools the IDE actually exposes are promised`() {
        val known = setOf("ide_search_text", "list_run_configurations")
        val text = IdeMcpPrompt.text(all, servers, known)
        assertTrue(text.contains("ide_search_text"))
        assertFalse(text.contains("ide_read_file"))
        assertFalse(text.contains("apply_patch"))
        assertTrue(text.contains("Every agent"))
    }

    @Test
    fun `rules are numbered in one sequence across servers`() {
        val numbers = IdeMcpPrompt.text(all, servers).lines().mapNotNull { it.substringBefore('.').toIntOrNull() }
        assertEquals((1..IdeRule.entries.size).toList(), numbers)
    }

    private companion object {
        const val BUDGET_CHARS = 2600
    }
}
