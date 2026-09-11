package dev.lain.claudejb.controller.session.events

import dev.lain.claudejb.controller.mcp.ToolOutput
import dev.lain.claudejb.controller.mcp.ToolOutputListener
import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.model.diff.DiffPresenter
import dev.lain.claudejb.model.diff.EditSnapshot
import dev.lain.claudejb.model.mcp.OwnTools
import dev.lain.claudejb.model.permission.scan.ToolInputScanner
import dev.lain.claudejb.model.protocol.ClaudeEvent
import dev.lain.claudejb.model.session.agents.AgentStatus
import dev.lain.claudejb.model.session.transcript.LiveLines
import dev.lain.claudejb.model.session.transcript.Speaker
import dev.lain.claudejb.model.session.transcript.ToolNaming
import dev.lain.claudejb.model.session.transcript.ToolState
import dev.lain.claudejb.model.session.transcript.TranscriptEntry
import kotlinx.serialization.json.JsonObject

class ToolEvents(
    private val s: ClaudeSession,
    private val edt: (() -> Unit) -> Unit,
    private val fireState: () -> Unit,
) {

    private val live = HashMap<String, Pair<TranscriptEntry, LiveLines>>()
    private val early = HashMap<String, LiveLines>()
    private val ownCalls = HashMap<String, OwnTools.Call>()

    init {
        s.project.messageBus.connect(s).subscribe(
            ToolOutput.TOPIC,
            ToolOutputListener { toolUseId, line -> edt { onLiveLine(toolUseId, line) } },
        )
    }

    private fun onLiveLine(toolUseId: String, line: String) {
        if (!s.transcript.knowsTool(toolUseId)) {
            early.getOrPut(toolUseId) { LiveLines() }.add(line)
            return
        }
        val (entry, ring) = live.getOrPut(toolUseId) {
            s.transcript.addToolOutput(toolUseId, "", meta = LIVE) to (early.remove(toolUseId) ?: LiveLines())
        }
        ring.add(line)
        s.transcript.replaceText(entry, ring.render())
    }

    private fun flushEarly(toolUseId: String) {
        val ring = early.remove(toolUseId) ?: return
        val entry = s.transcript.addToolOutput(toolUseId, ring.render(), meta = LIVE)
        live[toolUseId] = entry to ring
    }

    fun onToolUse(event: ClaudeEvent.ToolUse) = edt {
        if (event.parentToolUseId != null) {
            if (event.name in DiffPresenter.REVIEWABLE_TOOLS) {
                s.diffs.captureForReview(event.name, event.input, event.id)
            }
            return@edt
        }
        s.reconciler.onMessageBoundary()
        val own = OwnTools.parse(event.name, event.input)
        val review = own?.let { OwnTools.reviewAs(it, event.input, s.project.basePath) }
        s.transcript.add(
            Speaker.TOOL,
            own?.let { OwnTools.label(it, event.input) } ?: ToolNaming.formatToolUse(event.name, event.input, s.project.basePath),
            meta = event.name,
            toolUseId = event.id,
            parentToolUseId = event.parentToolUseId,
            toolState = ToolState.LOADING,
            filePath = ownPath(own, event),
            commandText = ToolInputScanner.commandText(event.input),
            messageText = ownMessage(own, review, event.input),
        )
        if (own != null) ownCalls[event.id] = own
        flushEarly(event.id)
        if (review != null) s.diffs.captureForReview(review.toolName, review.input, event.id)
        if (event.name in DiffPresenter.REVIEWABLE_TOOLS) {
            s.diffs.captureForReview(event.name, event.input, event.id)
            s.prompts.bindTool(event.id)
        }
    }

    private fun ownMessage(own: OwnTools.Call?, review: OwnTools.Review?, input: JsonObject): String? = when {
        own == null -> ToolInputScanner.messageText(input)
        review != null -> null
        else -> OwnTools.argsToon(input)
    }

    private fun ownPath(own: OwnTools.Call?, event: ClaudeEvent.ToolUse): String? =
        if (own != null) OwnTools.path(event.input) else ToolNaming.toolFilePath(event.name, event.input, s.project.basePath)

    fun onToolResult(event: ClaudeEvent.ToolResult) = edt {
        live.remove(event.toolUseId)
        early.remove(event.toolUseId)
        if (s.runningAgents.nodes.values.none { it.meta.toolUseId == event.toolUseId }) {
            s.transcript.setToolState(event.toolUseId, if (event.isError) ToolState.ERROR else ToolState.FINISHED)
        }
        if (s.backgroundTaskRegistry.observe(event)) {
            s.poll.ensureOutputTail()
            fireState()
        }
        val snap = s.diffs.onToolResult(event.toolUseId)
        if (!event.isError) {
            s.diffs.refreshTouched()
            if (ToolNaming.mayHaveWrittenUnknownFiles(s.transcript.toolNameOf(event.toolUseId))) {
                s.diffs.refreshProjectTree()
            }
        }
        val own = ownCalls.remove(event.toolUseId)
        val diff = snap?.let { proposed(it) }?.let { DiffPresenter.unifiedDiff(snap.beforeText, it) }?.takeIf { it.isNotBlank() }
        if (event.parentToolUseId != null) return@edt
        if (diff != null) {
            s.transcript.addToolOutput(event.toolUseId, diff, meta = "diff")
            return@edt
        }
        recordOutput(event, own)
    }

    private fun proposed(snap: EditSnapshot): String? = when (snap.toolName) {
        OwnTools.INSERT -> OwnTools.insertedText(snap.input, snap.beforeText)
        in DiffPresenter.REVIEWABLE_TOOLS -> DiffPresenter.proposedContent(snap.toolName, snap.input, snap.beforeText)
        else -> null
    }

    private fun recordOutput(event: ClaudeEvent.ToolResult, own: OwnTools.Call?) {
        val text = event.content.trim()
        if (text.isBlank()) return
        val decoded = if (!event.isError && own != null) OwnTools.decodeResult(text) else null
        if (decoded != null) {
            val read = own?.takeIf(OwnTools::isRead)?.let { OwnTools.readText(decoded) }
            if (read != null) {
                s.transcript.addToolOutput(event.toolUseId, read)
            } else {
                s.transcript.addToolOutput(event.toolUseId, decoded.toString(), meta = TOON)
            }
            return
        }
        val tags = buildList {
            if (s.transcript.isCommandCall(event.toolUseId)) add("command")
            if (event.isError) add("error")
        }
        s.transcript.addToolOutput(event.toolUseId, text, meta = tags.joinToString(" ").ifBlank { null })
    }

    private companion object {
        const val TOON = "toon"
        const val LIVE = "live"
    }

    fun labelAgentCards() {
        s.runningAgents.nodes.values.forEach { node ->
            val toolUseId = node.meta.toolUseId ?: return@forEach
            s.transcript.toolNameOf(toolUseId) ?: return@forEach
            s.transcript.setToolState(
                toolUseId,
                when (node.status) {
                    AgentStatus.RUNNING -> ToolState.RUNNING
                    AgentStatus.COMPLETED -> ToolState.FINISHED
                    else -> ToolState.ERROR
                },
            )
            val label = node.meta.description?.takeIf { it.isNotBlank() } ?: return@forEach
            s.transcript.setToolTitle(toolUseId, "${node.kindLabel} ($label)")
        }
    }
}
