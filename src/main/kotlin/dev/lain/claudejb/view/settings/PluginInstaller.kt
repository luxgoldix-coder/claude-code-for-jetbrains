package dev.lain.claudejb.view.settings

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import dev.lain.claudejb.model.session.launch.IdeServer

internal interface PluginPresence {
    fun isEnabled(pluginId: String): Boolean

    fun install(project: Project?, pluginId: String, onSuccess: () -> Unit)
}

internal object PlatformPluginPresence : PluginPresence {
    override fun isEnabled(pluginId: String): Boolean =
        PluginManagerCore.getPlugin(PluginId.getId(pluginId))?.isEnabled == true

    override fun install(project: Project?, pluginId: String, onSuccess: () -> Unit) =
        installAndEnable(project, setOf(PluginId.getId(pluginId)), showDialog = true, onSuccess = Runnable { onSuccess() })
}

internal class PluginInstaller(
    private val project: Project?,
    private val presence: PluginPresence = PlatformPluginPresence,
    private val confirm: (title: String, message: String) -> Boolean = { title, message ->
        Messages.showYesNoDialog(project, message, title, "Install", "Cancel", Messages.getQuestionIcon()) == Messages.YES
    },
) {

    fun ensureInstalled(server: IdeServer, onOutcome: (installed: Boolean) -> Unit) {
        if (presence.isEnabled(server.pluginId)) {
            onOutcome(true)
            return
        }
        if (!confirm("${server.label} is not installed", message(server))) {
            onOutcome(false)
            return
        }
        presence.install(project, server.pluginId) { onOutcome(true) }
    }

    companion object {
        fun message(server: IdeServer): String = buildString {
            append("Claude talks to the IDE through the ${server.label} plugin, which is not installed or is disabled.\n\n")
            if (server.thirdParty) {
                append("This is a third-party plugin by ${server.vendor}, not affiliated with JetBrains or with ")
                append("Claude Code Native. It runs inside the IDE with your privileges and listens on a localhost ")
                append("port that any local process can reach. Review it before you rely on it.\n\n")
            }
            append("Install and enable it now? The IDE's own plugin dialog takes over from here.")
        }
    }
}
