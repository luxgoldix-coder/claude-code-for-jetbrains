package dev.lain.claudejb.model.settings

import dev.lain.claudejb.model.protocol.ClaudeJson
import dev.lain.claudejb.model.protocol.EffortLevel
import dev.lain.claudejb.model.protocol.McpTransport
import dev.lain.claudejb.model.protocol.PermissionMode
import dev.lain.claudejb.model.protocol.models.ModelInfo
import kotlinx.serialization.json.JsonObject

object LaunchDefaults {

    const val DEFAULT_MODEL = "opus[1m]"

    const val RECOMMENDED_ALIAS = "default"

    private val TIER_ORDER = listOf("opus", "sonnet", "haiku")

    fun preferredDefault(models: List<ModelInfo>, pinned: String = DEFAULT_MODEL): String = when {
        models.isEmpty() -> pinned

        models.any { it.value == pinned } -> pinned

        models.any { it.value == RECOMMENDED_ALIAS } -> RECOMMENDED_ALIAS

        else -> TIER_ORDER.firstNotNullOfOrNull { tier ->
            models.firstOrNull { it.value.contains(tier, ignoreCase = true) }?.value
        } ?: models.first().value
    }

    const val THINKING_ON = 1

    val PERMISSION_MODES = PermissionMode.entries.map { it.wire }

    val EFFORT_LEVELS = EffortLevel.entries.map { it.wire }

    val SETTING_SOURCES = listOf("user", "project", "local")

    const val DEFAULT_IDE_MCP_PORT = 64342

    const val DEFAULT_INDEX_MCP_PORT = 29170

    const val DEFAULT_DEBUGGER_MCP_PORT = 29190

    private const val MAX_PORT = 65_535

    internal val VALID_PORTS = 1..MAX_PORT

    val IDE_MCP_TRANSPORTS = McpTransport.entries.map { it.wire }

    fun isValidMcpConfig(text: String): Boolean =
        text.isBlank() || (runCatching { ClaudeJson.parseToJsonElement(text) }.getOrNull() is JsonObject)
}
