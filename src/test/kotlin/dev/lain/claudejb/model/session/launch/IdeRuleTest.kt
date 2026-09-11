package dev.lain.claudejb.model.session.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdeRuleTest {

    @Test
    fun `every rule key is unique and names its server`() {
        assertEquals(IdeRule.entries.size, IdeRule.entries.map { it.key }.toSet().size)
        IdeRule.entries.filter { it.server != null }.forEach { rule ->
            assertTrue(rule.key.startsWith(rule.server!!.key + "."), rule.key)
            assertTrue(rule.tools.isNotEmpty(), rule.key)
        }
        IdeRule.common.forEach { assertTrue(it.key.startsWith("common."), it.key) }
    }

    @Test
    fun `the settings keep the rules as a csv that survives unknown keys and spaces`() {
        val parsed = IdeRule.parse(" index.read, debugger.debug ,gone.rule,")
        assertEquals(setOf(IdeRule.INDEX_READ, IdeRule.DEBUGGER_DEBUG), parsed)
        assertEquals("index.read,debugger.debug", IdeRule.csv(parsed))
        assertNull(IdeRule.of("nope"))
    }

    @Test
    fun `a rule is active only with its server on, and a common rule only with some server on`() {
        val picked = setOf(IdeRule.INDEX_READ, IdeRule.DEBUGGER_DEBUG, IdeRule.COMMON_AGENTS)
        assertEquals(emptySet<IdeRule>(), IdeRule.active(picked, emptySet()))
        assertEquals(setOf(IdeRule.INDEX_READ, IdeRule.COMMON_AGENTS), IdeRule.active(picked, setOf(IdeServer.INDEX)))
        assertEquals(picked, IdeRule.active(picked, setOf(IdeServer.INDEX, IdeServer.DEBUGGER)))
    }

    @Test
    fun `each server owns at least one rule and the third-party servers are named as such`() {
        IdeServer.entries.forEach { assertTrue(IdeRule.forServer(it).isNotEmpty(), it.key) }
        assertTrue(IdeServer.INDEX.thirdParty && IdeServer.DEBUGGER.thirdParty && !IdeServer.JETBRAINS.thirdParty)
        assertEquals("hechtcarmel", IdeServer.INDEX.vendor)
        assertEquals(IdeServer.INDEX, IdeServer.ofToolName("mcp__index__ide_read_file"))
        assertNull(IdeServer.ofToolName("Bash"))
    }
}
