package dev.lain.claudejb.view.settings.sections

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Panel
import dev.lain.claudejb.model.session.launch.GodMode
import dev.lain.claudejb.model.session.launch.IdeRule
import dev.lain.claudejb.model.session.launch.IdeServer
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.model.settings.LaunchDefaults
import dev.lain.claudejb.view.settings.PluginInstaller
import dev.lain.claudejb.view.settings.SettingsSection
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

internal class SettingsIdeMcpSection(installer: PluginInstaller) : SettingsSection {

    private val servers: Map<IdeServer, IdeServerControls> =
        IdeServer.PLUGINS.associateWith { IdeServerControls(it, installer) { syncEnabled() } }

    private val jetbrainsTransport = JComboBox(LaunchDefaults.IDE_MCP_TRANSPORTS.toTypedArray())
    private val jetbrainsPort = portSpinner(LaunchDefaults.DEFAULT_IDE_MCP_PORT)
    private val indexPort = portSpinner(LaunchDefaults.DEFAULT_INDEX_MCP_PORT)
    private val debuggerPort = portSpinner(LaunchDefaults.DEFAULT_DEBUGGER_MCP_PORT)
    private val commonRules = IdeRuleBoxes(IdeRule.common)

    private val ownServers = JBCheckBox(OWN_SERVERS_LABEL)
    private val approveClients = JBCheckBox("Ask me before an unexpected client may talk to our servers")

    private val enableAll = JButton("Turn " + GodMode.LABEL + " on — " + GodMode.TAGLINE).apply {
        addActionListener {
            ownServers.isSelected = true
            servers.values.forEach { it.turnOn() }
            commonRules.checkAll()
            syncEnabled()
        }
    }

    override fun addTo(panel: Panel) {
        panel.collapsibleGroup(TITLE) {
            row { cell(enableAll) }.rowComment(ENABLE_ALL_NOTE, MAX_LINE_LENGTH_WORD_WRAP)
            row { cell(ownServers) }.rowComment(OWN_SERVERS_NOTE, MAX_LINE_LENGTH_WORD_WRAP)
            row { cell(approveClients) }.rowComment(APPROVE_NOTE, MAX_LINE_LENGTH_WORD_WRAP)
            serverBlock(IdeServer.JETBRAINS, JETBRAINS_NOTE) {
                row("Transport:") { cell(jetbrainsTransport) }
                row("Port:") { cell(jetbrainsPort) }
            }
            serverBlock(IdeServer.INDEX, THIRD_PARTY_NOTE) { row("Port:") { cell(indexPort) } }
            serverBlock(IdeServer.DEBUGGER, THIRD_PARTY_NOTE) { row("Port:") { cell(debuggerPort) } }
            row("With any server:") { cell(commonRules.component) }.rowComment(RULES_NOTE, MAX_LINE_LENGTH_WORD_WRAP)
        }
    }

    private fun Panel.serverBlock(server: IdeServer, note: String, ports: Panel.() -> Unit) {
        val c = servers.getValue(server)
        row {
            cell(c.check)
            cell(c.action)
            cell(c.status)
        }
        ports()
        row { cell(c.rules.component) }.rowComment(note, MAX_LINE_LENGTH_WORD_WRAP)
    }

    override fun reset(s: ClaudeSettings.State) {
        ownServers.isSelected = s.ideMcp.enabled
        approveClients.isSelected = s.ideMcp.approveClients
        check(IdeServer.JETBRAINS).isSelected = s.ideMcpEnabled
        jetbrainsTransport.selectedItem = s.ideMcpTransport
        jetbrainsPort.value = s.ideMcpPort
        check(IdeServer.INDEX).isSelected = s.ideMcp.indexEnabled
        indexPort.value = s.ideMcp.indexPort
        check(IdeServer.DEBUGGER).isSelected = s.ideMcp.debuggerEnabled
        debuggerPort.value = s.ideMcp.debuggerPort
        val selected = IdeRule.parse(s.ideMcp.rules)
        servers.values.forEach {
            it.rules.setFrom(selected)
            it.refresh()
        }
        commonRules.setFrom(selected)
        syncEnabled()
    }

    override fun apply(s: ClaudeSettings.State) {
        s.ideMcp.enabled = ownServers.isSelected
        s.ideMcp.approveClients = approveClients.isSelected
        s.ideMcpEnabled = check(IdeServer.JETBRAINS).isSelected
        s.ideMcpTransport = transportText()
        s.ideMcpPort = port(jetbrainsPort)
        s.ideMcp.indexEnabled = check(IdeServer.INDEX).isSelected
        s.ideMcp.indexPort = port(indexPort)
        s.ideMcp.debuggerEnabled = check(IdeServer.DEBUGGER).isSelected
        s.ideMcp.debuggerPort = port(debuggerPort)
        s.ideMcp.rules = IdeRule.csv(selectedRules())
    }

    override fun changedFields(s: ClaudeSettings.State): List<Boolean> = listOf(
        ownServers.isSelected != s.ideMcp.enabled,
        approveClients.isSelected != s.ideMcp.approveClients,
        check(IdeServer.JETBRAINS).isSelected != s.ideMcpEnabled,
        transportText() != s.ideMcpTransport,
        port(jetbrainsPort) != s.ideMcpPort,
        check(IdeServer.INDEX).isSelected != s.ideMcp.indexEnabled,
        port(indexPort) != s.ideMcp.indexPort,
        check(IdeServer.DEBUGGER).isSelected != s.ideMcp.debuggerEnabled,
        port(debuggerPort) != s.ideMcp.debuggerPort,
        selectedRules() != IdeRule.parse(s.ideMcp.rules),
    )

    private fun check(server: IdeServer) = servers.getValue(server).check

    private fun selectedRules(): Set<IdeRule> =
        servers.values.flatMap { it.rules.selected() }.toSet() + commonRules.selected()

    private fun syncEnabled() {
        servers.values.forEach { it.syncEnabled() }
        jetbrainsTransport.isEnabled = check(IdeServer.JETBRAINS).isSelected
        jetbrainsPort.isEnabled = check(IdeServer.JETBRAINS).isSelected
        indexPort.isEnabled = check(IdeServer.INDEX).isSelected
        debuggerPort.isEnabled = check(IdeServer.DEBUGGER).isSelected
        commonRules.setEnabled(servers.values.any { it.isOn() })
    }

    private fun transportText() = (jetbrainsTransport.selectedItem as? String) ?: "sse"

    private fun port(spinner: JSpinner) = (spinner.value as Number).toInt()

    private fun portSpinner(default: Int) = JSpinner(SpinnerNumberModel(default, MIN_PORT, MAX_PORT, 1))

    private companion object {
        const val MIN_PORT = 1
        const val MAX_PORT = 65_535

        const val TITLE = "Claude IDE Integration"

        const val OWN_SERVERS_LABEL = "Enable this plugin's own servers — code, run, vcs and ops, over Unix sockets"

        const val OWN_SERVERS_NOTE =
            "Four MCP servers of this plugin's own, with no port and nothing to install. Each offers its tools on " +
                "demand, so the session pays only for the domains it uses."

        const val APPROVE_NOTE =
            "Our servers already refuse anyone without the session's token. This adds a notification with Allow " +
                "and Reject for a connection the plugin did not launch itself; unanswered, it is rejected."

        const val ENABLE_ALL_NOTE =
            "One switch, every IDE MCP server and every rule. Claude then reads, searches, edits, refactors, builds, " +
                "tests and debugs through the IDE itself: faster, cheaper in tokens, and the results land where you " +
                "work. Each server needs its plugin installed and the IDE restarted once; the buttons say where " +
                "each one stands. Fine-tune below; the flame in the chat bar lights when everything is on."

        const val JETBRAINS_NOTE =
            "⚠ JetBrains' own MCP Server plugin, bundled with recent IDEs. <code>sse</code> and " +
                "<code>streamable-http</code> expose a localhost port any local process can reach; <code>stdio</code> " +
                "launches a helper instead. Tool calls are still gated by the permission prompt and by the guard."

        const val THIRD_PARTY_NOTE =
            "⚠ Third-party plugin by hechtcarmel, not affiliated with JetBrains or with this plugin. It runs with your " +
                "IDE's privileges and listens on a localhost port any local process can reach. Install goes through " +
                "the IDE's own plugin dialog."

        const val RULES_NOTE =
            "Each rule adds one short instruction to Claude's system prompt, repeated on every turn as a hook so it " +
                "does not drift, naming the exact tools to use. Only " +
                "tools the IDE actually exposes are named; a rule whose tools are not exposed is left out and the " +
                "MCP card in the chat says which tools to enable."
    }
}
