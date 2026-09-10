package dev.lain.claudejb.session

import dev.lain.claudejb.diff.DiffPresenter
import dev.lain.claudejb.permission.ToolInputScanner
import dev.lain.claudejb.protocol.ClaudeEvent

class ToolEvents(
    private val s: ClaudeSession,
    private val edt: (() -> Unit) -> Unit,
    private val fireState: () -> Unit,
) {

    fun onToolUse(event: ClaudeEvent.ToolUse) = edt {
        if (event.parentToolUseId != null) {
            if (event.name in DiffPresenter.REVIEWABLE_TOOLS) {
                s.diffs.captureForReview(event.name, event.input, event.id)
            }
            return@edt
        }
        s.reconciler.onMessageBoundary()
        s.transcript.add(
            Speaker.TOOL,
            ToolNaming.formatToolUse(event.name, event.input, s.workingDir),
            meta = event.name,
            toolUseId = event.id,
            parentToolUseId = event.parentToolUseId,
            toolState = ToolState.LOADING,
            filePath = ToolNaming.toolFilePath(event.name, event.input, s.workingDir),
            commandText = ToolInputScanner.commandText(event.input),
            messageText = ToolInputScanner.messageText(event.input),
        )
        if (event.name in DiffPresenter.REVIEWABLE_TOOLS) {
            s.diffs.captureForReview(event.name, event.input, event.id)
            s.prompts.bindTool(event.id)
        }
    }

    fun onToolResult(event: ClaudeEvent.ToolResult) = edt {
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
            s.transcript.addToolOutput(event.toolUseId, diff, parentToolUseId = event.parentToolUseId, meta = "diff")
            return@edt
        }
        val text = event.content.trim()
        if (text.isBlank()) return@edt
        val tags = buildList {
            if (s.transcript.isCommandCall(event.toolUseId)) add("command")
            if (event.isError) add("error")
        }
        s.transcript.addToolOutput(
            event.toolUseId,
            text,
            parentToolUseId = event.parentToolUseId,
            meta = tags.joinToString(" ").ifBlank { null },
        )
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
