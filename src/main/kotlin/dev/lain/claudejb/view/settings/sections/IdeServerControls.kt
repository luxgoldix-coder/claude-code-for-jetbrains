package dev.lain.claudejb.view.settings.sections

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import dev.lain.claudejb.model.session.launch.IdeServer
import dev.lain.claudejb.view.settings.PluginInstaller
import dev.lain.claudejb.view.settings.PluginState
import javax.swing.JButton

internal class IdeServerControls(
    private val installer: PluginInstaller,
    private val onChange: () -> Unit,
) {

    val check = JBCheckBox(LABEL)
    val action = JButton(INSTALL)
    val status = JBLabel()

    private var state = PluginState.MISSING

    init {
        check.addActionListener { onChange() }
        action.addActionListener {
            if (state == PluginState.PENDING_RESTART) installer.restart() else install()
        }
    }

    fun refresh() {
        state = installer.state()
        action.text = when (state) {
            PluginState.MISSING -> INSTALL
            PluginState.PENDING_RESTART -> RESTART
            PluginState.READY -> INSTALLED
        }
        action.isEnabled = state != PluginState.READY
        status.text = if (state == PluginState.PENDING_RESTART) RESTART_NOTE else ""
        status.isVisible = status.text.isNotEmpty()
    }

    fun syncEnabled() {
        check.isEnabled = state == PluginState.READY
    }

    private fun install() {
        installer.install {
            refresh()
            if (state == PluginState.READY) check.isSelected = true
            onChange()
        }
    }

    private companion object {
        const val INSTALL = "Install MCP server"
        const val RESTART = "Restart the IDE"
        const val INSTALLED = "Already installed"
        const val RESTART_NOTE = "Restart the IDE to finish installing"

        val LABEL: String = "Enable " + IdeServer.JETBRAINS.label + " — JetBrains' own server, as an optional extra"
    }
}
