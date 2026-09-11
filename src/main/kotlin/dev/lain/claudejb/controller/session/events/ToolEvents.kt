package dev.lain.claudejb.controller.session.events

import dev.lain.claudejb.controller.mcp.ToolOutput
import dev.lain.claudejb.controller.mcp.ToolOutputListener
import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.model.diff.DiffPresenter
import dev.lain.claudejb.model.mcp.OwnTools
import dev.lain.claudejb.model.permission.scan.ToolInputScanner
import dev.lain.claudejb.model.protocol.ClaudeEvent
import dev.lain.claudejb.model.session.agents.AgentStatus
import dev.lain.claudejb.model.session.transcript.LiveLines
import dev.lain.claudejb.model.session.transcript.Speaker
import dev.lain.claudejb.model.session.transcript.ToolNaming
import dev.lain.claudejb.model.session.transcript.ToolState
import dev.lain.claudejb.model.session.transcript.TranscriptEntry

class ToolEvents(
    private val s: ClaudeSession,
    private val edt: (() -> Unit) -> Unit,
    private val fireState: () -> Unit,
) {

    private val live = HashMap<String, Pair<TranscriptEntry, LiveLines>>()

    init {
        s.project.messageBus.connect(s).subscribe(
            ToolOutput.TOPIC,
            ToolOutputListener { toolUseId, line -> edt { onLiveLine(toolUseId, line) } },
        )
    }

    private fun onLiveLine(toolUseId: String, line: String) {
        if (!s.transcript.knowsTool(toolUseId)) return
        val (entry, ring) = live.getOrPut(toolUseId) { s.transcript.addToolOutput(toolUseId, "", meta = LIVE) to LiveLines() }
        ring.add(line)
        s.transcript.replaceText(entry, ring.render())
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
        s.transcript.add(
            Speaker.TOOL,
            own?.let(OwnTools::label) ?: ToolNaming.formatToolUse(event.name, event.input, s.project.basePath),
            meta = event.name,
            toolUseId = event.id,
            parentToolUseId = event.parentToolUseId,
            toolState = ToolState.LOADING,
            filePath = ToolNaming.toolFilePath(event.name, event.input, s.project.basePath),
            commandText = ToolInputScanner.commandText(event.input),
            messageText = if (own != null) OwnTools.argsToon(event.input) else ToolInputScanner.messageText(event.input),
        )
        if (event.name in DiffPresenter.REVIEWABLE_TOOLS) {
            s.diffs.captureForReview(event.name, event.input, event.id)
            s.prompts.bindTool(event.id)
        }
    }

    fun onToolResult(event: ClaudeEvent.ToolResult) = edt {
        live.remove(event.toolUseId)
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
        val diff = if (snap != null && snap.toolName in DiffPresenter.REVIEWABLE_TOOLS) {
            DiffPresenter.proposedContent(snap.toolName, snap.input, snap.beforeText)
                ?.let { DiffPresenter.unifiedDiff(snap.beforeText, it) }
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        if (event.parentToolUseId != null) return@edt
        if (diff != null) {
            s.transcript.addToolOutput(event.toolUseId, diff, meta = "diff")
            return@edt
        }
        recordOutput(event)
    }

    private fun recordOutput(event: ClaudeEvent.ToolResult) {
        val text = event.content.trim()
        if (text.isBlank()) return
        val decoded = if (!event.isError && OwnTools.isOwn(s.transcript.toolNameOf(event.toolUseId))) OwnTools.decodeResult(text) else null
        if (decoded != null) {
            s.transcript.addToolOutput(event.toolUseId, decoded.toString(), meta = TOON)
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
