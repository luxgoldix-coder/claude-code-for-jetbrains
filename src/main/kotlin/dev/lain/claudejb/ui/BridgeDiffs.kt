package dev.lain.claudejb.ui

import dev.lain.claudejb.diff.DiffPresenter
import dev.lain.claudejb.diff.EditSnapshot
import dev.lain.claudejb.ui.jcef.JcefTranscriptPayload
import dev.lain.claudejb.ui.jcef.Msg

internal class BridgeDiffs(private val panel: JcefChatPanel) {

    fun handle(m: Msg.Diffs) {
        when (m) {
            is Msg.ViewDiff -> panel.cardSession(m.scope).cards.pending().firstOrNull { it.requestId == m.id }
                ?.let { DiffPresenter.openDiff(panel.project, it.toolName, it.input) }

            is Msg.ViewDiffByTool -> snapshotAnywhere(m.toolUseId)
                ?.let { DiffPresenter.openDiff(panel.project, it.toolName, it.input, it.beforeText) }

            is Msg.RevertEdit -> panel.edits.rewindOrRevert(m.toolUseId)

            is Msg.Open -> panel.links.open(m.url)

            is Msg.ResolveLinks -> resolveLinks(m)
        }
    }

    private fun snapshotAnywhere(toolUseId: String): EditSnapshot? =
        panel.session.cards.editSnapshot(toolUseId) ?: panel.gitChat.session().cards.editSnapshot(toolUseId)

    private fun resolveLinks(m: Msg.ResolveLinks) {
        if (m.paths.isEmpty() && m.symbols.isEmpty()) return
        val project = panel.project
        panel.host.execBuilt("window.cc.links") {
            val resolved = LinkResolver.resolvePaths(project, m.paths) + LinkResolver.resolveSymbols(project, m.symbols)
            resolved.takeIf { it.isNotEmpty() }?.let { JcefTranscriptPayload.linksJson(m.rowId, it) }
        }
    }
}
