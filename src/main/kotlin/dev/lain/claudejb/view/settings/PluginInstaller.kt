package dev.lain.claudejb.view.settings

import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import dev.lain.claudejb.model.session.launch.IdeServer
import dev.lain.claudejb.util.PluginIds

internal enum class PluginState { MISSING, PENDING_RESTART, READY }

internal interface PluginPresence {
    fun state(pluginId: String): PluginState

    fun install(project: Project?, pluginId: String, onSuccess: () -> Unit)

    fun restart()
}

internal object PlatformPluginPresence : PluginPresence {
    override fun state(pluginId: String): PluginState {
        val id = PluginIds.of(pluginId)
        return when {
            InstalledPluginsState.getInstanceIfLoaded()?.wasInstalled(id) == true -> PluginState.PENDING_RESTART
            PluginManagerCore.isLoaded(id) -> PluginState.READY
            else -> PluginState.MISSING
        }
    }

    override fun install(project: Project?, pluginId: String, onSuccess: () -> Unit) =
        installAndEnable(project, setOf(PluginIds.of(pluginId)), showDialog = true, onSuccess = Runnable { onSuccess() })

    override fun restart() = ApplicationManagerEx.getApplicationEx().restart(true)
}

internal class PluginInstaller(
    private val project: Project?,
    private val presence: PluginPresence = PlatformPluginPresence,
    private val confirm: (title: String, message: String, yes: String) -> Boolean = { title, message, yes ->
        Messages.showYesNoDialog(project, message, title, yes, "Cancel", Messages.getQuestionIcon()) == Messages.YES
    },
) {

    fun state(server: IdeServer): PluginState = presence.state(server.plugin)

    fun install(server: IdeServer, onOutcome: (PluginState) -> Unit) {
        val now = state(server)
        if (now != PluginState.MISSING) {
            onOutcome(now)
            return
        }
        if (!confirm("${server.label} is not installed", installMessage(server), "Install")) {
            onOutcome(PluginState.MISSING)
            return
        }
        presence.install(project, server.plugin) { onOutcome(state(server)) }
    }

    fun restart(server: IdeServer) {
        if (confirm("Restart to finish installing", restartMessage(server), "Restart")) presence.restart()
    }

    companion object {
        fun installMessage(server: IdeServer): String = buildString {
            append("Claude talks to the IDE through the ${server.label} plugin, which is not installed or is disabled.\n\n")
            if (server.thirdParty) {
                append("This is a third-party plugin by ${server.vendor}, not affiliated with JetBrains or with ")
                append("Claude Code Native. It runs inside the IDE with your privileges and listens on a localhost ")
                append("port that any local process can reach. Review it before you rely on it.\n\n")
            }
            append("Install and enable it now? The IDE's own plugin dialog takes over from here.")
        }

        fun restartMessage(server: IdeServer): String =
            "The ${server.label} plugin is installed but will only load after the IDE restarts. " +
                "Restart now? Your work is saved first."
    }
}
