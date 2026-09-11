package dev.lain.claudejb.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

object ProtocolParser {

    private val TOP_LEVEL_DECODERS: Map<String, (JsonObject) -> List<ClaudeEvent>> = buildMap {
        fun <T> typed(type: String, serializer: KSerializer<T>, wrap: (T) -> ClaudeEvent) {
            put(type) { root -> decode(root, serializer, wrap, type) }
        }
        put("system", ::parseSystem)
        put("assistant", MessageParsers::parseAssistant)
        put("user", MessageParsers::parseUser)
        put("stream_event", MessageParsers::parseStreamEvent)
        put("control_request", ControlParsers::parseControlRequest)
        put("control_response", ControlParsers::parseControlResponse)
        put("control_cancel_request", ControlParsers::parseControlCancel)
        put("rate_limit_event", ControlParsers::parseRateLimit)
        put("keep_alive") { emptyList() }
        typed("result", ResultMessage.serializer(), ClaudeEvent::Result)
        typed("auth_status", AuthStatusInfo.serializer(), ClaudeEvent::AuthStatus)
        typed("tool_progress", ToolProgressInfo.serializer(), ClaudeEvent::ToolProgress)
        typed("tool_use_summary", ToolUseSummaryInfo.serializer(), ClaudeEvent::ToolUseSummary)
        typed("prompt_suggestion", PromptSuggestionInfo.serializer(), ClaudeEvent::PromptSuggestion)
    }

    fun parse(line: String): List<ClaudeEvent> {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return emptyList()
        val root = runCatching { ClaudeJson.parseToJsonElement(trimmed) }.getOrNull() as? JsonObject
            ?: return listOf(ClaudeEvent.Other("?", null, JsonObject(emptyMap())))
        val type = root.str("type") ?: return listOf(ClaudeEvent.Other("?", null, root))

        val decoder = TOP_LEVEL_DECODERS[type]
            ?: return listOf(ClaudeEvent.Other(type, root.str("subtype"), root))
        return decoder(root)
    }

    private val SYSTEM_DECODERS: Map<String, (JsonObject) -> List<ClaudeEvent>> = buildMap {
        fun <T> typed(subtype: String, serializer: KSerializer<T>, wrap: (T) -> ClaudeEvent) {
            put(subtype) { root -> decode(root, serializer, wrap, "system") }
        }
        typed("init", SystemInit.serializer(), ClaudeEvent::Init)
        typed("task_started", TaskStartedInfo.serializer(), ClaudeEvent::TaskStarted)
        typed("task_progress", TaskProgressInfo.serializer(), ClaudeEvent::TaskProgress)
        typed("task_updated", TaskUpdatedInfo.serializer(), ClaudeEvent::TaskUpdated)
        typed("task_notification", TaskNotificationInfo.serializer(), ClaudeEvent::TaskNotification)
        typed("thinking_tokens", ThinkingTokensInfo.serializer(), ClaudeEvent::ThinkingTokens)
        typed("notification", NotificationInfo.serializer(), ClaudeEvent::Notification)
        typed("permission_denied", PermissionDeniedInfo.serializer(), ClaudeEvent::PermissionDenied)
        typed("session_state_changed", SessionStateInfo.serializer(), ClaudeEvent::SessionStateChanged)
        typed("api_retry", ApiRetryInfo.serializer(), ClaudeEvent::ApiRetry)
        typed("commands_changed", CommandsChangedInfo.serializer(), ClaudeEvent::CommandsChanged)
        typed("memory_recall", MemoryRecallInfo.serializer(), ClaudeEvent::MemoryRecall)
        typed("files_persisted", FilesPersistedInfo.serializer(), ClaudeEvent::FilesPersisted)
        typed("plugin_install", PluginInstallInfo.serializer(), ClaudeEvent::PluginInstall)
        typed("hook_started", HookStartedInfo.serializer(), ClaudeEvent::HookStarted)
        typed("hook_progress", HookProgressInfo.serializer(), ClaudeEvent::HookProgress)
        typed("hook_response", HookResponseInfo.serializer(), ClaudeEvent::HookResponse)
        typed("mirror_error", MirrorErrorInfo.serializer(), ClaudeEvent::MirrorError)
        typed("model_refusal_fallback", ModelRefusalFallbackInfo.serializer(), ClaudeEvent::ModelRefusalFallback)
        typed("informational", InformationalInfo.serializer(), ClaudeEvent::Informational)
        typed("model_refusal_no_fallback", ModelRefusalNoFallbackInfo.serializer(), ClaudeEvent::ModelRefusalNoFallback)
        typed("worker_shutting_down", WorkerShuttingDownInfo.serializer(), ClaudeEvent::WorkerShuttingDown)
        typed("background_tasks_changed", BackgroundTasksChangedInfo.serializer(), ClaudeEvent::BackgroundTasksChanged)
        typed("control_request_progress", ControlRequestProgressInfo.serializer(), ClaudeEvent::ControlRequestProgress)
        put("local_command_output") { listOf(ClaudeEvent.LocalCommandOutput(it.str("content").orEmpty())) }
        put("status", ::parseStatus)
        put("compact_boundary", ::parseCompactBoundary)
    }

    private fun parseSystem(root: JsonObject): List<ClaudeEvent> {
        val subtype = root.str("subtype")
        val decoder = SYSTEM_DECODERS[subtype] ?: return listOf(ClaudeEvent.Other("system", subtype, root))
        return decoder(root)
    }

    private fun <T> decode(
        root: JsonObject,
        serializer: KSerializer<T>,
        wrap: (T) -> ClaudeEvent,
        fallbackType: String,
    ): List<ClaudeEvent> = runCatching {
        listOf(wrap(ClaudeJson.decodeFromJsonElement(serializer, root)))
    }.getOrDefault(listOf(ClaudeEvent.Other(fallbackType, root.str("subtype"), root)))

    private fun parseStatus(root: JsonObject): List<ClaudeEvent> {
        root.str("compact_result")?.let { result ->
            val text = if (result == "success") {
                "✓ Conversation compacted"
            } else {
                "Compaction failed" + (root.str("compact_error")?.let { ": $it" } ?: "")
            }
            return listOf(ClaudeEvent.StatusNotice(text))
        }
        return when (root.str("status")) {
            "compacting" -> listOf(ClaudeEvent.StatusNotice("Compacting conversation…"))
            else -> emptyList()
        }
    }

    private fun parseCompactBoundary(root: JsonObject): List<ClaudeEvent> {
        val meta = root["compact_metadata"] as? JsonObject ?: return emptyList()
        val trigger = meta.str("trigger") ?: "manual"
        val pre = meta.intField("pre_tokens")
        val post = meta.intField("post_tokens")
        val ms = meta.intField("duration_ms")
        val tokens = if (pre != null && post != null) "${tokens(pre)} → ${tokens(post)} tokens" else "context reduced"
        val took = ms?.let { " · ${it / 1000}s" } ?: ""
        return listOf(ClaudeEvent.StatusNotice("Context compacted ($trigger): $tokens$took"))
    }

    private fun tokens(n: Int): String =
        if (n >= 1000) String.format(java.util.Locale.ROOT, "%.1fk", n / 1000.0) else n.toString()
}
