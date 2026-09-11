package dev.lain.claudejb.model.session.launch

enum class IdeServer(
    val key: String,
    val label: String,
    val own: Boolean,
    val pluginId: String? = null,
    val vendor: String? = null,
) {
    CODE("code", "Code", own = true),
    RUN("run", "Run", own = true),
    VCS("vcs", "VCS", own = true),
    OPS("ops", "Ops", own = true),
    JETBRAINS("jetbrains", "JetBrains MCP Server", own = false, pluginId = "com.intellij.mcpServer", vendor = "JetBrains"),
    INDEX(
        "index",
        "IDE Index MCP Server",
        own = false,
        pluginId = "com.github.hechtcarmel.jetbrainsindexmcpplugin",
        vendor = "hechtcarmel",
    ),
    DEBUGGER(
        "debugger",
        "Debugger MCP Server",
        own = false,
        pluginId = "com.github.hechtcarmel.jetbrainsdebuggermcpplugin",
        vendor = "hechtcarmel",
    ),
    ;

    val mcpName: String get() = key

    val toolPrefix: String get() = "mcp__" + key + "__"

    val thirdParty: Boolean get() = vendor != null && vendor != JETBRAINS.vendor

    val plugin: String get() = requireNotNull(pluginId) { key + " is a server of this plugin's own; there is no plugin to install" }

    fun streamableHttpUrl(port: Int): String? = when (this) {
        INDEX -> "http://127.0.0.1:$port/index-mcp/streamable-http"
        DEBUGGER -> "http://127.0.0.1:$port/debugger-mcp/streamable-http"
        else -> null
    }

    companion object {
        val OWN: List<IdeServer> = entries.filter { it.own }

        val PLUGINS: List<IdeServer> = entries.filter { it.pluginId != null }

        fun ofToolName(toolName: String): IdeServer? = PLUGINS.firstOrNull { toolName.startsWith(it.toolPrefix) }
    }
}
