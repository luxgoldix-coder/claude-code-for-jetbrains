package dev.lain.claudejb.model.session.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IdeToolInventoryTest {

    @Test
    fun `only IDE server tools are kept, without their server prefix`() {
        val known = IdeToolInventory.fromToolNames(
            listOf("Bash", "mcp__index__ide_read_file", "mcp__debugger__set_breakpoint", "mcp__other__thing", "mcp__jetbrains__apply_patch"),
        )
        assertEquals(setOf("ide_read_file", "set_breakpoint", "apply_patch"), known)
        assertEquals("apply_patch,ide_read_file,set_breakpoint", IdeToolInventory.csv(known))
        assertEquals(known, IdeToolInventory.parse(IdeToolInventory.csv(known) + ", ,"))
    }
}
