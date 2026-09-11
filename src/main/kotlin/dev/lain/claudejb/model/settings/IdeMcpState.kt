package dev.lain.claudejb.model.settings

@kotlinx.serialization.Serializable
data class IdeMcpState(
    @JvmField var enabled: Boolean = false,
    @JvmField var approveClients: Boolean = false,
    @JvmField var indexEnabled: Boolean = false,
    @JvmField var indexPort: Int = LaunchDefaults.DEFAULT_INDEX_MCP_PORT,
    @JvmField var debuggerEnabled: Boolean = false,
    @JvmField var debuggerPort: Int = LaunchDefaults.DEFAULT_DEBUGGER_MCP_PORT,
    @JvmField var rules: String = "",
    @JvmField var knownTools: String = "",
)
