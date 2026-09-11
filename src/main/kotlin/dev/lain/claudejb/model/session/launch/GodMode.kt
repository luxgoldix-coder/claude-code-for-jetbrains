package dev.lain.claudejb.model.session.launch

import dev.lain.claudejb.model.settings.ClaudeSettings

object GodMode {
    const val LABEL = "Claude God Mode"
    const val TAGLINE = "Claude becomes one with your IDE"

    fun isOn(s: ClaudeSettings.State): Boolean = s.ideMcp.enabled

    fun set(s: ClaudeSettings.State, on: Boolean) {
        s.ideMcp.enabled = on
    }
}
