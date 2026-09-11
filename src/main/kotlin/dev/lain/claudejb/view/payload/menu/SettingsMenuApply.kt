package dev.lain.claudejb.view.payload.menu

import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.controller.session.SessionLiveSettings

internal object SettingsMenuApply {

    private val LIVE_SETTERS: Map<String, (SessionLiveSettings, String) -> Unit> = mapOf(
        JcefSettingsMenu.MODEL to { s, value -> s.changeModel(value) },
        JcefSettingsMenu.EFFORT to { s, value -> s.changeEffort(value) },
        JcefSettingsMenu.MODE to { s, value -> s.changePermissionMode(value) },
    )

    fun toSession(session: ClaudeSession, key: String, on: Boolean) {
        if (!on) return
        val setter = LIVE_SETTERS[key.substringBefore(':', missingDelimiterValue = "")] ?: return
        setter(session.settings, key.substringAfter(':', missingDelimiterValue = ""))
    }
}
