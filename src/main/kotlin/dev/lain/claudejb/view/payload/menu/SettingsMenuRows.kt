package dev.lain.claudejb.ui.jcef

import dev.lain.claudejb.permission.SecurityCategory
import dev.lain.claudejb.permission.SecurityRule
import dev.lain.claudejb.protocol.EffortLevel
import dev.lain.claudejb.protocol.ModelInfo
import dev.lain.claudejb.protocol.PermissionMode
import dev.lain.claudejb.session.ClaudeSession
import dev.lain.claudejb.session.ToolNaming
import dev.lain.claudejb.settings.ClaudeSettings
import dev.lain.claudejb.settings.GuardMode
import dev.lain.claudejb.settings.LaunchDefaults
import dev.lain.claudejb.settings.SecuritySuspensions
import dev.lain.claudejb.ui.jcef.JcefSettingsMenu.ALLOW
import dev.lain.claudejb.ui.jcef.JcefSettingsMenu.ALWAYS
import dev.lain.claudejb.ui.jcef.JcefSettingsMenu.APPROVAL
import dev.lain.claudejb.ui.jcef.JcefSettingsMenu.DENY
import dev.lain.claudejb.ui.jcef.JcefSettingsMenu.EFFORT
import dev.lain.claudejb.ui.jcef.JcefSettingsMenu.GUARD_MODE
import dev.lain.claudejb.ui.jcef.JcefSettingsMenu.MODE
import dev.lain.claudejb.ui.jcef.JcefSettingsMenu.MODEL
import dev.lain.claudejb.ui.jcef.JcefSettingsMenu.REMOTE_CONTROL
import dev.lain.claudejb.ui.jcef.JcefSettingsMenu.RULE
import dev.lain.claudejb.ui.jcef.JcefSettingsMenu.SOURCE
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

internal object SettingsMenuRows {

    internal data class Selected(
        val models: List<ModelInfo>,
        val model: String,
        val effort: String?,
        val mode: String,
        val approvals: Map<SecurityRule, Set<String>> = emptyMap(),
        val remoteControl: Boolean = false,
    )

    fun json(scope: String, state: ClaudeSettings.State, session: ClaudeSession): JsonArray =
        json(scope, state, selectedIn(session))

    internal fun json(scope: String, state: ClaudeSettings.State, selected: Selected): JsonArray = buildJsonArray {
        modelRows(selected)
        effortRows(selected)
        modeRows(selected)
        remoteControlRows(selected)
        chatRows(state)
        securityRows(scope, state)
        sessionApprovalRows(selected.approvals)
        sourceRows(state)
        toolRows(ALLOW, "Allowed tools", state.allowedTools, deferred = true)
        toolRows(DENY, "Disallowed tools", state.disallowedTools, deferred = true)
        toolRows(ALWAYS, "Always allowed tools", state.alwaysAllowTools, deferred = false)
        mcpRows(state)
    }

    private fun JsonArrayBuilder.modelRows(selected: Selected) {
        selected.models.filter { it.value != LaunchDefaults.RECOMMENDED_ALIAS }.forEach { m ->
            val label = JcefModelLabels.modelDisplayLabel(m)
            entry("$MODEL:${m.value}", "Model", label, m.value == selected.model, radio = true)
        }
    }

    private fun JsonArrayBuilder.effortRows(selected: Selected) {
        EffortLevel.entries.forEach { level ->
            val label = level.wire.replaceFirstChar { it.uppercase() }
            entry("$EFFORT:${level.wire}", "Effort", label, level.wire == selected.effort, radio = true)
        }
    }

    private fun JsonArrayBuilder.modeRows(selected: Selected) {
        LaunchDefaults.PERMISSION_MODES.forEach { wire ->
            entry("$MODE:$wire", "Permission mode", PermissionMode.labelFor(wire), wire == selected.mode, radio = true)
        }
    }

    private fun JsonArrayBuilder.remoteControlRows(selected: Selected) {
        entry(REMOTE_CONTROL, "Remote control", "Drive this chat from claude.ai", selected.remoteControl, hostOwned = true)
    }

    private fun JsonArrayBuilder.chatRows(s: ClaudeSettings.State) {
        entry("restoreChats", "Chat", "Restore open chats on startup", s.restoreOpenChatsOnStartup)
        entry("reduceMotion", "Chat", "Reduce motion", s.reduceMotion)
        entry("checkpointing", "Chat", "Let Claude rewind file changes", s.enableFileCheckpointing)
        entry("partialMessages", "Chat", "Stream partial messages", s.includePartialMessages)
    }

    private fun JsonArrayBuilder.securityRows(scope: String, s: ClaudeSettings.State) {
        val disabled = JcefSettingsMenu.csvItems(s.disabledSecurityRules)
        val now = System.currentTimeMillis()
        val suspended = SecuritySuspensions.active(s.securityRuleSuspensions, now) +
            SecuritySuspensions.sessionSuspended(scope)
        val mode = if (SecuritySuspensions.guardSuspended(scope, s, now)) {
            GuardMode.ALLOW_ALL
        } else {
            GuardMode.from(s.guardMode) ?: GuardMode.DEFAULT
        }
        GuardMode.entries.forEach { m ->
            entry("$GUARD_MODE:${m.wire}", "Guard mode", m.label, m == mode, radio = true)
        }
        SecurityCategory.entries.forEach { category ->
            SecurityRule.of(category).forEach { rule ->
                val enforced = rule.name !in disabled && rule !in suspended
                entry("$RULE:${rule.name}", "Security", rule.label, enforced, sub = category.label)
            }
        }
    }

    private fun JsonArrayBuilder.sessionApprovalRows(approvals: Map<SecurityRule, Set<String>>) {
        approvals.forEach { (rule, commands) ->
            commands.forEach { command ->
                entry("$APPROVAL:${rule.name}:$command", "Approved in this chat", command, true, sub = rule.label)
            }
        }
    }

    private fun JsonArrayBuilder.sourceRows(s: ClaudeSettings.State) {
        LaunchDefaults.SETTING_SOURCES.forEach { source ->
            val label = source.replaceFirstChar { it.uppercase() }
            entry("$SOURCE:$source", "Setting sources", label, JcefSettingsMenu.csvHas(s.settingSources, source), deferred = true)
        }
    }

    private fun JsonArrayBuilder.toolRows(prefix: String, group: String, csv: String, deferred: Boolean) {
        ToolNaming.BUILTIN_TOOLS.forEach { tool ->
            entry("$prefix:$tool", group, tool, JcefSettingsMenu.csvHas(csv, tool), deferred = deferred)
        }
    }

    private fun JsonArrayBuilder.mcpRows(s: ClaudeSettings.State) {
        entry("ideMcp", "MCP", "JetBrains MCP server", s.ideMcpEnabled, deferred = true)
        entry("strictMcp", "MCP", "Only the MCP servers configured here", s.strictMcpConfig, deferred = true)
    }

    private fun JsonArrayBuilder.entry(
        key: String,
        group: String,
        label: String,
        on: Boolean,
        radio: Boolean = false,
        deferred: Boolean = false,
        sub: String? = null,
        hostOwned: Boolean = false,
    ) = addJsonObject {
        put("key", key)
        put("group", group)
        if (sub != null) put("sub", sub)
        put("label", label)
        put("on", on)
        put("type", if (radio) TYPE_RADIO else TYPE_CHECK)
        put("deferred", deferred)
        if (hostOwned) put("hostOwned", true)
    }

    private fun selectedIn(session: ClaudeSession) = Selected(
        models = session.catalog.models,
        model = session.launch.model ?: session.catalog.preferredDefaultModel(),
        effort = session.launch.effort,
        mode = session.launch.permissionMode,
        approvals = session.guard.approvals.all(),
        remoteControl = session.remote.enabled,
    )

    private const val TYPE_CHECK = "check"
    private const val TYPE_RADIO = "radio"
}
