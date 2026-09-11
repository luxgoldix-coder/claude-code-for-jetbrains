package dev.lain.claudejb.view.settings.sections

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import dev.lain.claudejb.model.session.launch.IdeRule
import dev.lain.claudejb.model.session.launch.IdeServer
import dev.lain.claudejb.view.settings.PluginInstaller
import dev.lain.claudejb.view.settings.PluginState
import javax.swing.JButton

internal class IdeServerControls(
    private val server: IdeServer,
    private val installer: PluginInstaller,
    private val onChange: () -> Unit,
) {

    val check = JBCheckBox(LABELS.getValue(server))
    val rules = IdeRuleBoxes(IdeRule.forServer(server))
    val action = JButton(INSTALL)
    val status = JBLabel()

    private var state = PluginState.MISSING

    init {
        check.addActionListener {
            if (check.isSelected && rules.selected().isEmpty()) rules.checkAll()
            onChange()
        }
        action.addActionListener {
            if (state == PluginState.PENDING_RESTART) installer.restart(server) else install()
        }
    }

    fun isOn(): Boolean = state == PluginState.READY && check.isSelected

    fun turnOn() {
        when (state) {
            PluginState.READY -> select()
            PluginState.MISSING -> install()
            PluginState.PENDING_RESTART -> Unit
        }
    }

    fun refresh() {
        state = installer.state(server)
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
        rules.setEnabled(isOn())
    }

    private fun select() {
        check.isSelected = true
        rules.checkAll()
    }

    private fun install() {
        installer.install(server) {
            refresh()
            if (state == PluginState.READY) select()
            onChange()
        }
    }

    private companion object {
        const val INSTALL = "Install MCP server"
        const val RESTART = "Restart the IDE"
        const val INSTALLED = "Already installed"
        const val RESTART_NOTE = "Restart the IDE to finish installing"

        val LABELS: Map<IdeServer, String> = mapOf(
            IdeServer.JETBRAINS to "Enable ${IdeServer.JETBRAINS.label} — the IDE's own files, problems, run configurations and VCS",
            IdeServer.INDEX to "Enable ${IdeServer.INDEX.label} — code intelligence, edits, builds and tests",
            IdeServer.DEBUGGER to "Enable ${IdeServer.DEBUGGER.label} — run configurations and live debugging",
        )
    }
}
