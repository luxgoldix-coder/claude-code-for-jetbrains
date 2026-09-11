package dev.lain.claudejb.view.settings.sections

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Panel
import dev.lain.claudejb.model.session.launch.GodMode
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.model.settings.LaunchDefaults
import dev.lain.claudejb.view.settings.PluginInstaller
import dev.lain.claudejb.view.settings.SettingsSection
import javax.swing.JComboBox
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

internal class SettingsIdeMcpSection(installer: PluginInstaller) : SettingsSection {

    private val godMode = JBCheckBox(GodMode.LABEL + " — " + GodMode.TAGLINE)
    private val jetbrains = IdeServerControls(installer) { syncEnabled() }
    private val jetbrainsTransport = JComboBox(LaunchDefaults.IDE_MCP_TRANSPORTS.toTypedArray())
    private val jetbrainsPort = JSpinner(SpinnerNumberModel(LaunchDefaults.DEFAULT_IDE_MCP_PORT, MIN_PORT, MAX_PORT, 1))

    override fun addTo(panel: Panel) {
        panel.collapsibleGroup(TITLE) {
            row { cell(godMode) }.rowComment(GOD_MODE_NOTE, MAX_LINE_LENGTH_WORD_WRAP)
            row {
                cell(jetbrains.check)
                cell(jetbrains.action)
                cell(jetbrains.status)
            }
            row("Transport:") { cell(jetbrainsTransport) }
            row("Port:") { cell(jetbrainsPort) }.rowComment(JETBRAINS_NOTE, MAX_LINE_LENGTH_WORD_WRAP)
        }
    }

    override fun reset(s: ClaudeSettings.State) {
        godMode.isSelected = GodMode.isOn(s)
        jetbrains.check.isSelected = s.ideMcpEnabled
        jetbrainsTransport.selectedItem = s.ideMcpTransport
        jetbrainsPort.value = s.ideMcpPort
        jetbrains.refresh()
        syncEnabled()
    }

    override fun apply(s: ClaudeSettings.State) {
        GodMode.set(s, godMode.isSelected)
        s.ideMcpEnabled = jetbrains.check.isSelected
        s.ideMcpTransport = transportText()
        s.ideMcpPort = port()
    }

    override fun changedFields(s: ClaudeSettings.State): List<Boolean> = listOf(
        godMode.isSelected != GodMode.isOn(s),
        jetbrains.check.isSelected != s.ideMcpEnabled,
        transportText() != s.ideMcpTransport,
        port() != s.ideMcpPort,
    )

    private fun syncEnabled() {
        jetbrains.syncEnabled()
        jetbrainsTransport.isEnabled = jetbrains.check.isSelected
        jetbrainsPort.isEnabled = jetbrains.check.isSelected
    }

    private fun transportText() = (jetbrainsTransport.selectedItem as? String) ?: "sse"

    private fun port() = (jetbrainsPort.value as Number).toInt()

    private companion object {
        const val MIN_PORT = 1
        const val MAX_PORT = 65_535

        const val TITLE = "Claude IDE Integration"

        const val GOD_MODE_NOTE =
            "One switch. Claude reaches the IDE through four MCP servers of this plugin's own — code, run, vcs and " +
                "ops — over Unix sockets, with no port and nothing to install. Each server offers its tools on " +
                "demand, so the session pays only for the domains it uses. Takes effect on the next chat; the " +
                "flame in the chat bar lights when it is on."

        const val JETBRAINS_NOTE =
            "⚠ JetBrains' own MCP Server plugin, bundled with recent IDEs, as an optional extra outside God Mode. " +
                "<code>sse</code> and <code>streamable-http</code> expose a localhost port any local process can " +
                "reach; <code>stdio</code> launches a helper instead. Tool calls are still gated by the permission " +
                "prompt and by the guard."
    }
}
