package dev.lain.claudejb.view.settings.sections

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Panel
import dev.lain.claudejb.model.session.launch.IdeRule
import dev.lain.claudejb.model.session.launch.IdeServer
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.model.settings.LaunchDefaults
import dev.lain.claudejb.view.settings.PluginInstaller
import dev.lain.claudejb.view.settings.SettingsSection
import javax.swing.JButton
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

internal class SettingsIdeMcpSection(
    private val installer: PluginInstaller,
    private val jetbrainsCheck: JBCheckBox,
) : SettingsSection {

    private val indexCheck = JBCheckBox("Enable ${IdeServer.INDEX.label} — code intelligence, edits, builds and tests")
    private val indexPort = portSpinner(LaunchDefaults.DEFAULT_INDEX_MCP_PORT)
    private val debuggerCheck = JBCheckBox("Enable ${IdeServer.DEBUGGER.label} — run configurations and live debugging")
    private val debuggerPort = portSpinner(LaunchDefaults.DEFAULT_DEBUGGER_MCP_PORT)

    private val rules: Map<IdeServer?, IdeRuleBoxes> =
        (IdeServer.entries.map { it } + null).associateWith { server ->
            IdeRuleBoxes(if (server == null) IdeRule.common else IdeRule.forServer(server))
        }

    private val enableAll = JButton("Enable all IDE MCP integrations").apply {
        addActionListener {
            jetbrainsCheck.isSelected = true
            indexCheck.isSelected = true
            debuggerCheck.isSelected = true
            rules.values.forEach { it.checkAll() }
            listOf(IdeServer.JETBRAINS, IdeServer.INDEX, IdeServer.DEBUGGER).forEach { ensureInstalled(it) }
            syncEnabled()
        }
    }

    init {
        indexCheck.addActionListener { onServerToggled(IdeServer.INDEX, indexCheck) }
        debuggerCheck.addActionListener { onServerToggled(IdeServer.DEBUGGER, debuggerCheck) }
        jetbrainsCheck.addActionListener { onServerToggled(IdeServer.JETBRAINS, jetbrainsCheck) }
    }

    override fun addTo(panel: Panel) {
        panel.collapsibleGroup("IDE integration") {
            row { cell(enableAll) }.rowComment(ENABLE_ALL_NOTE, MAX_LINE_LENGTH_WORD_WRAP)
            row { cell(indexCheck) }
            row("Index port:") { cell(indexPort) }.rowComment(THIRD_PARTY_NOTE, MAX_LINE_LENGTH_WORD_WRAP)
            row { cell(rules.getValue(IdeServer.INDEX).component) }
            row { cell(debuggerCheck) }
            row("Debugger port:") { cell(debuggerPort) }.rowComment(THIRD_PARTY_NOTE, MAX_LINE_LENGTH_WORD_WRAP)
            row { cell(rules.getValue(IdeServer.DEBUGGER).component) }
            row("JetBrains MCP Server rules:") { cell(rules.getValue(IdeServer.JETBRAINS).component) }
                .rowComment(JETBRAINS_NOTE, MAX_LINE_LENGTH_WORD_WRAP)
            row("With any server:") { cell(rules.getValue(null).component) }
                .rowComment(RULES_NOTE, MAX_LINE_LENGTH_WORD_WRAP)
        }
    }

    override fun reset(s: ClaudeSettings.State) {
        indexCheck.isSelected = s.ideMcp.indexEnabled
        indexPort.value = s.ideMcp.indexPort
        debuggerCheck.isSelected = s.ideMcp.debuggerEnabled
        debuggerPort.value = s.ideMcp.debuggerPort
        val selected = IdeRule.parse(s.ideMcp.rules)
        rules.values.forEach { it.setFrom(selected) }
        syncEnabled()
    }

    override fun apply(s: ClaudeSettings.State) {
        s.ideMcp.indexEnabled = indexCheck.isSelected
        s.ideMcp.indexPort = port(indexPort)
        s.ideMcp.debuggerEnabled = debuggerCheck.isSelected
        s.ideMcp.debuggerPort = port(debuggerPort)
        s.ideMcp.rules = IdeRule.csv(selectedRules())
    }

    override fun changedFields(s: ClaudeSettings.State): List<Boolean> = listOf(
        indexCheck.isSelected != s.ideMcp.indexEnabled,
        port(indexPort) != s.ideMcp.indexPort,
        debuggerCheck.isSelected != s.ideMcp.debuggerEnabled,
        port(debuggerPort) != s.ideMcp.debuggerPort,
        selectedRules() != IdeRule.parse(s.ideMcp.rules),
    )

    private fun selectedRules(): Set<IdeRule> = rules.values.flatMap { it.selected() }.toSet()

    private fun onServerToggled(server: IdeServer, check: JBCheckBox) {
        if (check.isSelected) {
            if (rules.getValue(server).selected().isEmpty()) rules.getValue(server).checkAll()
            ensureInstalled(server)
        }
        syncEnabled()
    }

    private fun ensureInstalled(server: IdeServer) {
        installer.ensureInstalled(server) { installed ->
            if (!installed) checkOf(server).isSelected = false
            syncEnabled()
        }
    }

    private fun checkOf(server: IdeServer): JBCheckBox = when (server) {
        IdeServer.INDEX -> indexCheck
        IdeServer.DEBUGGER -> debuggerCheck
        IdeServer.JETBRAINS -> jetbrainsCheck
    }

    private fun syncEnabled() {
        IdeServer.entries.forEach { rules.getValue(it).setEnabled(checkOf(it).isSelected) }
        indexPort.isEnabled = indexCheck.isSelected
        debuggerPort.isEnabled = debuggerCheck.isSelected
        rules.getValue(null).setEnabled(IdeServer.entries.any { checkOf(it).isSelected })
    }

    private fun port(spinner: JSpinner) = (spinner.value as Number).toInt()

    private fun portSpinner(default: Int) = JSpinner(SpinnerNumberModel(default, MIN_PORT, MAX_PORT, 1))

    private companion object {
        const val MIN_PORT = 1
        const val MAX_PORT = 65_535

        const val ENABLE_ALL_NOTE =
            "Switches every IDE MCP server on and every rule with it. Claude then reads, searches, edits, builds, " +
                "tests and debugs through the IDE instead of through its own tools and shell commands."

        const val THIRD_PARTY_NOTE =
            "⚠ Third-party plugin by hechtcarmel, not affiliated with JetBrains or with this plugin. It runs with your " +
                "IDE's privileges and listens on a localhost port any local process can reach. If it is missing, " +
                "switching it on offers to install it through the IDE's own dialog."

        const val JETBRAINS_NOTE = "These apply once the JetBrains MCP server above is switched on."

        const val RULES_NOTE =
            "Each rule adds one short instruction to Claude's system prompt, naming the exact tools to use. Only " +
                "tools the IDE actually exposes are named; a rule whose tools are not exposed is left out and the " +
                "MCP card in the chat says which tools to enable."
    }
}
