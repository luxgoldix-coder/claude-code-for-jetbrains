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

    fun state(): PluginState = presence.state(IdeServer.JETBRAINS_PLUGIN_ID)

    fun install(onOutcome: (PluginState) -> Unit) {
        val now = state()
        if (now != PluginState.MISSING) {
            onOutcome(now)
            return
        }
        if (!confirm(IdeServer.JETBRAINS.label + " is not installed", INSTALL_MESSAGE, "Install")) {
            onOutcome(PluginState.MISSING)
            return
        }
        presence.install(project, IdeServer.JETBRAINS_PLUGIN_ID) { onOutcome(state()) }
    }

    fun restart() {
        if (confirm("Restart to finish installing", RESTART_MESSAGE, "Restart")) presence.restart()
    }

    companion object {
        val INSTALL_MESSAGE: String =
            "Claude can also talk to the IDE through the " + IdeServer.JETBRAINS.label + " plugin, which is not " +
                "installed or is disabled.\n\nInstall and enable it now? The IDE's own plugin dialog takes over from here."

        val RESTART_MESSAGE: String =
            "The " + IdeServer.JETBRAINS.label + " plugin is installed but will only load after the IDE restarts. " +
                "Restart now? Your work is saved first."
    }
}
