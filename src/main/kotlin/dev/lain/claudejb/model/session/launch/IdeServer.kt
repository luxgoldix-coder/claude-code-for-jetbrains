package dev.lain.claudejb.model.session.launch

enum class IdeServer(
    val key: String,
    val label: String,
    val pluginId: String,
    val vendor: String,
    val thirdParty: Boolean,
) {
    JETBRAINS(
        key = "jetbrains",
        label = "JetBrains MCP Server",
        pluginId = "com.intellij.mcpServer",
        vendor = "JetBrains",
        thirdParty = false,
    ),
    INDEX(
        key = "index",
        label = "IDE Index MCP Server",
        pluginId = "com.github.hechtcarmel.jetbrainsindexmcpplugin",
        vendor = "hechtcarmel",
        thirdParty = true,
    ),
    DEBUGGER(
        key = "debugger",
        label = "Debugger MCP Server",
        pluginId = "com.github.hechtcarmel.jetbrainsdebuggermcpplugin",
        vendor = "hechtcarmel",
        thirdParty = true,
    ),
    ;

    val mcpName: String get() = key

    val toolPrefix: String get() = "mcp__" + key + "__"

    fun streamableHttpUrl(port: Int): String? = when (this) {
        INDEX -> "http://127.0.0.1:$port/index-mcp/streamable-http"
        DEBUGGER -> "http://127.0.0.1:$port/debugger-mcp/streamable-http"
        JETBRAINS -> null
    }

    companion object {
        fun of(key: String): IdeServer? = entries.firstOrNull { it.key == key }

        fun ofToolName(toolName: String): IdeServer? = entries.firstOrNull { toolName.startsWith(it.toolPrefix) }
    }
}
