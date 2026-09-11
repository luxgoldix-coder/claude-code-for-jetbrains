package dev.lain.claudejb.model.session.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdeMcpPromptTest {

    private val own = IdeServer.OWN.toSet()
    private val plugins = IdeServer.PLUGINS.toSet()
    private val all = IdeRule.entries.toSet()

    @Test
    fun `no server of our own and no rule means no block, and a plugin server alone without rules earns none`() {
        assertEquals("", IdeMcpPrompt.text(emptySet()))
        assertEquals("", IdeMcpPrompt.text(setOf(IdeServer.JETBRAINS)))
        assertEquals("", IdeMcpPrompt.rulesBlock(all, own, emptySet()))
    }

    @Test
    fun `only the servers of our own that are on are listed, each with what it is for`() {
        val text = IdeMcpPrompt.text(setOf(IdeServer.CODE, IdeServer.VCS, IdeServer.JETBRAINS))
        assertTrue(text.contains("code ("))
        assertTrue(text.contains("vcs ("))
        assertFalse(text.contains("run ("))
        assertFalse(text.contains("ops ("))
        assertFalse(text.contains("jetbrains"))
    }

    @Test
    fun `the paragraph names the three meta-tools and no tool of any domain, reads as an order, and fits`() {
        val text = IdeMcpPrompt.text(own)
        listOf("domains()", "tools(domain)", "run(tool, args)").forEach { assertTrue(text.contains(it), it) }
        assertFalse(SNAKE_CASE.containsMatchIn(text), "a domain tool is named: " + SNAKE_CASE.find(text)?.value)
        assertTrue(text.startsWith(IdeMcpPrompt.OPEN) && text.endsWith(IdeMcpPrompt.CLOSE))
        assertEquals(3, text.lines().size, "open, one paragraph, close")
        listOf("subagent", "verbatim", "fallback", "domains() first").forEach { assertTrue(text.contains(it), it) }
        listOf("prefer", "try to", "if possible", "consider", "when possible").forEach {
            assertFalse(text.lowercase().contains(it), it)
        }
        assertTrue(text.length < BUDGET_PARAGRAPH, "length=" + text.length)
    }

    @Test
    fun `rules of a server that is off are left out, and every rule names one of its tools in one sequence`() {
        val onlyIndex = IdeMcpPrompt.text(setOf(IdeServer.INDEX), all)
        assertTrue(onlyIndex.contains("ide_read_file"))
        assertFalse(onlyIndex.contains("start_debug_session"))
        assertFalse(onlyIndex.contains("apply_patch"))
        assertTrue(onlyIndex.contains("Always:"))
        val text = IdeMcpPrompt.rulesBlock(all, plugins, emptySet())
        val numbered = text.lines().filter { it.substringBefore('.').toIntOrNull() != null }
        assertEquals(IdeRule.entries.size, numbered.size)
        assertEquals((1..IdeRule.entries.size).toList(), numbered.map { it.substringBefore('.').toInt() })
        IdeRule.entries.filter { it.tools.isNotEmpty() }.forEach { rule ->
            assertTrue(numbered.any { line -> rule.tools.any { line.contains(it) } }, rule.key)
        }
        assertTrue(text.startsWith(IdeMcpPrompt.OPEN) && text.endsWith(IdeMcpPrompt.CLOSE))
        assertTrue(text.length < BUDGET_RULES, "length=" + text.length)
    }

    @Test
    fun `only tools the IDE actually exposes are promised`() {
        val known = setOf("ide_search_text", "list_run_configurations")
        val text = IdeMcpPrompt.text(plugins, all, known)
        assertTrue(text.contains("ide_search_text"))
        assertFalse(text.contains("ide_read_file"))
        assertFalse(text.contains("apply_patch"))
        assertTrue(text.contains("Every agent"))
    }

    @Test
    fun `with everything on there is one block holding both parts, and the hook's block is contained in it`() {
        val text = IdeMcpPrompt.text(own + plugins, all)
        assertEquals(1, text.lines().count { it == IdeMcpPrompt.OPEN })
        assertEquals(1, text.lines().count { it == IdeMcpPrompt.CLOSE })
        assertTrue(text.contains("domains()") && text.contains("ide_read_file"))
        assertTrue(text.length < BUDGET_ALL, "length=" + text.length)
        val hook = IdeMcpPrompt.rulesBlock(all, own + plugins, emptySet())
        hook.lines().forEach { assertTrue(it in text.lines(), it) }
    }

    private companion object {
        const val BUDGET_PARAGRAPH = 800
        const val BUDGET_RULES = 2600
        const val BUDGET_ALL = 3400

        val SNAKE_CASE = Regex("[a-z]+_[a-z_]+")
    }
}
