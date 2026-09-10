package dev.lain.claudejb.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

class HookBroker {

    fun parse(request: JsonObject): HookContext? {
        val input = request["input"] as? JsonObject ?: return null
        val hookEventName = input.str("hook_event_name") ?: return null
        val callbackId = request.str("callback_id").orEmpty()
        val toolUseId = request.str("tool_use_id") ?: input.str("tool_use_id")
        return HookContext(
            callbackId = callbackId,
            hookEventName = hookEventName,
            toolUseId = toolUseId,
            toolName = input.str("tool_name"),
            toolInput = input["tool_input"] as? JsonObject,
            sessionId = input.str("session_id"),
            cwd = input.str("cwd"),
            message = input.str("message"),
            title = input.str("title"),
            filePath = input.str("file_path"),
            fileEvent = input.str("event"),
            source = input.str("source"),
            trigger = input.str("trigger"),
            reason = input.str("reason"),
            raw = input,
        )
    }

    fun buildResponse(callbackId: String): JsonObject = buildJsonObject {
        if (callbackId.isNotEmpty()) put("callback_id", callbackId)
        put("continue", true)
    }

    fun sideEffects(ctx: HookContext): List<HookSideEffect> = when (ctx.hookEventName) {
        "Notification" -> listOfNotNull(
            ctx.message?.takeIf { it.isNotBlank() }?.let { HookSideEffect.NotifyUser(it, ctx.title) },
        )

        "FileChanged" -> listOfNotNull(
            ctx.filePath?.takeIf { it.isNotBlank() }?.let { HookSideEffect.RefreshFile(it, ctx.fileEvent) },
        )

        "SessionStart", "SessionEnd", "Stop" -> listOf(HookSideEffect.Marker(ctx.hookEventName, ctx.source ?: ctx.reason))

        "PreCompact" -> listOf(
            HookSideEffect.TranscriptNote("Compacting conversation" + (ctx.trigger?.let { " ($it)" } ?: "") + "…"),
        )

        "PostCompact" -> listOf(HookSideEffect.TranscriptNote("Conversation compacted."))

        else -> emptyList()
    }
}

data class HookContext(
    val callbackId: String,
    val hookEventName: String,
    val toolUseId: String? = null,
    val toolName: String? = null,
    val toolInput: JsonObject? = null,
    val sessionId: String? = null,
    val cwd: String? = null,
    val message: String? = null,
    val title: String? = null,
    val filePath: String? = null,
    val fileEvent: String? = null,
    val source: String? = null,
    val trigger: String? = null,
    val reason: String? = null,
    val raw: JsonObject? = null,
)

sealed interface HookSideEffect {
    data class NotifyUser(val message: String, val title: String? = null) : HookSideEffect

    data class RefreshFile(val path: String, val event: String? = null) : HookSideEffect

    data class Marker(val event: String, val detail: String? = null) : HookSideEffect

    data class TranscriptNote(val text: String) : HookSideEffect
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
