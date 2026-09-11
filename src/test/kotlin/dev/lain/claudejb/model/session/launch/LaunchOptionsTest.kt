package dev.lain.claudejb.model.session.launch

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LaunchOptionsTest {

    private val base = LaunchOptions(ideMcpEnabled = true, ideMcpPort = 64342, customMcpServers = """{"a":{}}""")

    @Test
    fun `the MCP configuration is read at launch, so any MCP field that changes differs`() {
        assertTrue(base.mcpDiffers(base.copy(ideMcpEnabled = false)))
        assertTrue(base.mcpDiffers(base.copy(ideMcpTransport = "streamable-http")))
        assertTrue(base.mcpDiffers(base.copy(ideMcpPort = 64343)))
        assertTrue(base.mcpDiffers(base.copy(customMcpServers = """{"b":{}}""")))
        assertTrue(base.mcpDiffers(base.copy(strictMcpConfig = true)))
    }

    @Test
    fun `the IDE servers and the IDE rules ride the launch too, so they differ as well`() {
        assertTrue(base.mcpDiffers(base.copy(indexMcpEnabled = true)))
        assertTrue(base.mcpDiffers(base.copy(indexMcpPort = 29171)))
        assertTrue(base.mcpDiffers(base.copy(debuggerMcpEnabled = true)))
        assertTrue(base.mcpDiffers(base.copy(debuggerMcpPort = 29191)))
        assertTrue(base.mcpDiffers(base.copy(ideRules = setOf(IdeRule.INDEX_READ))))
        assertFalse(base.mcpDiffers(base.copy(knownIdeTools = setOf("ide_read_file"))))
    }

    @Test
    fun `a change the binary takes live is not an MCP difference`() {
        assertFalse(base.mcpDiffers(base.copy(model = "opus", permissionMode = "plan", effort = "high", thinkingTokens = 8)))
        assertFalse(base.mcpDiffers(base))
    }
}
