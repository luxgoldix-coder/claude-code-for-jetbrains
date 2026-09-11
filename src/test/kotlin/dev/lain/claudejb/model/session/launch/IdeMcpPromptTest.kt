package dev.lain.claudejb.model.session.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdeMcpPromptTest {

    private val own = IdeServer.OWN.toSet()

    @Test
    fun `no server of our own means no block, and the JetBrains server alone does not earn one`() {
        assertEquals("", IdeMcpPrompt.text(emptySet()))
        assertEquals("", IdeMcpPrompt.text(setOf(IdeServer.JETBRAINS)))
    }

    @Test
    fun `only the servers that are on are listed, each with what it is for`() {
        val text = IdeMcpPrompt.text(setOf(IdeServer.CODE, IdeServer.VCS, IdeServer.JETBRAINS))
        assertTrue(text.contains("code ("))
        assertTrue(text.contains("vcs ("))
        assertFalse(text.contains("run ("))
        assertFalse(text.contains("ops ("))
        assertFalse(text.contains("jetbrains"))
    }

    @Test
    fun `the block names the three meta-tools and no tool of any domain`() {
        val text = IdeMcpPrompt.text(own)
        listOf("domains()", "tools(domain)", "run(tool, args)").forEach { assertTrue(text.contains(it), it) }
        assertFalse(SNAKE_CASE.containsMatchIn(text), "a domain tool is named: " + SNAKE_CASE.find(text)?.value)
        assertFalse(text.contains("mcp__"))
    }

    @Test
    fun `the block reads as an order, is one paragraph, and carries the common rules`() {
        val text = IdeMcpPrompt.text(own)
        assertTrue(text.startsWith(IdeMcpPrompt.OPEN) && text.endsWith(IdeMcpPrompt.CLOSE))
        assertEquals(3, text.lines().size, "open, one paragraph, close")
        listOf("subagent", "verbatim", "fallback", "domains() first").forEach { assertTrue(text.contains(it), it) }
        listOf("prefer", "try to", "if possible", "consider", "when possible").forEach {
            assertFalse(text.lowercase().contains(it), it)
        }
    }

    @Test
    fun `the whole block with every server on stays under the tightened budget`() {
        val text = IdeMcpPrompt.text(own)
        assertTrue(text.length < BUDGET_CHARS, "length=" + text.length)
    }

    private companion object {
        const val BUDGET_CHARS = 800

        val SNAKE_CASE = Regex("[a-z]+_[a-z_]+")
    }
}
