package dev.lain.claudejb.model.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IdeRulesAdoptionTest {

    @Test
    fun `rules saved under a previous catalogue become the whole current one, so an upgrade lands in God Mode`() {
        val previous = "index.read,debugger.debug,jetbrains.build,common.agents,common.tools,common.fallback,common.report"
        assertEquals(LaunchDefaults.DEFAULT_IDE_RULES, IdeRulesAdoption.adopted(previous))
        val s = ClaudeSettings.State().apply { ideMcp.rules = previous }
        IdeRulesAdoption.adopt(s)
        assertEquals(LaunchDefaults.DEFAULT_IDE_RULES, s.ideMcp.rules)
    }

    @Test
    fun `a choice made on the current catalogue is kept, everything off included`() {
        listOf("", "code.read,common.agents", " vcs.write , ops.ide ,", LaunchDefaults.DEFAULT_IDE_RULES).forEach { csv ->
            assertEquals(csv, IdeRulesAdoption.adopted(csv), csv)
        }
    }
}
