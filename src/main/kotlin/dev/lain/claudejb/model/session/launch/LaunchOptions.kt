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
    val indexMcpEnabled: Boolean = false,
    val indexMcpPort: Int = LaunchDefaults.DEFAULT_INDEX_MCP_PORT,
    val debuggerMcpEnabled: Boolean = false,
    val debuggerMcpPort: Int = LaunchDefaults.DEFAULT_DEBUGGER_MCP_PORT,
    val ideRules: Set<IdeRule> = emptySet(),
    val knownIdeTools: Set<String> = emptySet(),
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
            indexMcpEnabled != other.indexMcpEnabled ||
            indexMcpPort != other.indexMcpPort ||
            debuggerMcpEnabled != other.debuggerMcpEnabled ||
            debuggerMcpPort != other.debuggerMcpPort ||
            ideRules != other.ideRules

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
                indexMcpEnabled = s.ideMcp.indexEnabled,
                indexMcpPort = s.ideMcp.indexPort.takeIf { it in LaunchDefaults.VALID_PORTS } ?: LaunchDefaults.DEFAULT_INDEX_MCP_PORT,
                debuggerMcpEnabled = s.ideMcp.debuggerEnabled,
                debuggerMcpPort = s.ideMcp.debuggerPort.takeIf { it in LaunchDefaults.VALID_PORTS }
                    ?: LaunchDefaults.DEFAULT_DEBUGGER_MCP_PORT,
                ideRules = IdeRule.parse(s.ideMcp.rules),
                knownIdeTools = s.ideMcp.knownTools.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
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
