package dev.lain.claudejb.ui

import com.intellij.openapi.diagnostic.thisLogger
import dev.lain.claudejb.ui.jcef.Msg

internal class BridgeNavigation(private val panel: JcefChatPanel) {

    private val log = thisLogger()

    fun handle(m: Msg.Navigation) {
        when (m) {
            is Msg.RevealAgent -> panel.agentTabs.revealElsewhere(m.chatId) { it.agentTabs.revealFromHost(m) }

            is Msg.RevealBackgroundTask ->
                panel.agentTabs.revealElsewhere(m.chatId) { it.transcript.showBackgroundTask(m.taskId) }

            Msg.ShowChatTranscript -> panel.transcript.showTranscript(null)

            is Msg.SelectChat -> withStrip("select chat ${m.chatId}") { it.selectById(m.chatId) }

            is Msg.CloseChat -> withStrip("close chat ${m.chatId}") { it.closeById(m.chatId) }

            is Msg.SelectAgent -> panel.transcript.showTranscript(m.agentId.ifBlank { null })

            is Msg.CloseAgent -> panel.agentTabs.closeAgent(m.agentId)
        }
    }

    fun withStrip(what: String, block: (ChatTabsPanel) -> Unit) {
        val strip = panel.chatStrip()
        if (strip == null) {
            log.warn("Claude Code: no chat strip to $what — the press was dropped")
            return
        }
        block(strip)
    }
}
