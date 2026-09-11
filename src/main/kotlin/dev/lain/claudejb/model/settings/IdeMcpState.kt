package dev.lain.claudejb.model.settings

@kotlinx.serialization.Serializable
data class IdeMcpState(
    @JvmField var enabled: Boolean = false,
    @JvmField var approveClients: Boolean = false,
)
