package dev.lain.claudejb.model.session.launch

enum class IdeServer(val key: String, val label: String, val own: Boolean) {
    CODE("code", "Code", own = true),
    RUN("run", "Run", own = true),
    VCS("vcs", "VCS", own = true),
    OPS("ops", "Ops", own = true),
    JETBRAINS("jetbrains", "JetBrains MCP Server", own = false),
    ;

    val mcpName: String get() = key

    companion object {
        const val JETBRAINS_PLUGIN_ID = "com.intellij.mcpServer"

        val OWN: List<IdeServer> = entries.filter { it.own }
    }
}
