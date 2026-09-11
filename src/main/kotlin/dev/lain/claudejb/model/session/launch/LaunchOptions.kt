package dev.lain.claudejb.model.session.launch

import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.model.settings.LaunchDefaults

data class LaunchOptions(
    val model: String? = null,
    val effort: String? = null,
    val permissionMode: String = "default",
    val thinkingTokens: Int? = null,
    val allowedTools: String = "",
    val disallowedTools: String = "",
    val settingSources: String = "user,project,local",
    val includePartialMessages: Boolean = true,
    val ideMcpEnabled: Boolean = false,
    val ideMcpTransport: String = "sse",
    val ideMcpPort: Int = LaunchDefaults.DEFAULT_IDE_MCP_PORT,
    val customMcpServers: String = "",
    val ideIntegration: Boolean = false,
    val ideSockets: Map<IdeServer, String> = emptyMap(),
    val maxTurns: Int? = null,
    val maxBudgetUsd: Double? = null,
    val fallbackModel: String? = null,
    val addDirs: List<String> = emptyList(),
    val betas: String? = null,
    val strictMcpConfig: Boolean = false,
    val sessionId: String? = null,
    val fork: Boolean = false,
) {

    fun mcpDiffers(other: LaunchOptions): Boolean =
        ideMcpEnabled != other.ideMcpEnabled ||
            ideMcpTransport != other.ideMcpTransport ||
            ideMcpPort != other.ideMcpPort ||
            customMcpServers != other.customMcpServers ||
            strictMcpConfig != other.strictMcpConfig ||
            ideIntegration != other.ideIntegration ||
            ideSockets != other.ideSockets

    companion object {

        fun from(settings: ClaudeSettings): LaunchOptions {
            val s = settings.state
            return LaunchOptions(
                model = s.model.ifBlank { null },
                effort = s.effort.ifBlank { null },
                permissionMode = s.permissionMode.ifBlank { "default" },
                thinkingTokens = s.thinkingTokens.takeIf { it > 0 },
                allowedTools = s.allowedTools,
                disallowedTools = s.disallowedTools,
                settingSources = s.settingSources,
                includePartialMessages = s.includePartialMessages,
                ideMcpEnabled = s.ideMcpEnabled,
                ideMcpTransport = s.ideMcpTransport.ifBlank { "sse" },
                ideMcpPort = s.ideMcpPort.takeIf { it in LaunchDefaults.VALID_PORTS } ?: LaunchDefaults.DEFAULT_IDE_MCP_PORT,
                customMcpServers = s.customMcpServers,
                ideIntegration = s.ideMcp.enabled,
                maxTurns = settings.maxTurns,
                maxBudgetUsd = settings.maxBudgetUsd,
                fallbackModel = settings.fallbackModel,
                addDirs = settings.addDirs,
                betas = settings.betas,
                strictMcpConfig = settings.strictMcpConfig,
            )
        }
    }
}
