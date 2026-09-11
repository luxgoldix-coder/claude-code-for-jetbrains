package dev.lain.claudejb.ui

import dev.lain.claudejb.ui.jcef.JcefBridge
import dev.lain.claudejb.ui.jcef.Msg

internal class ChatBridgeRouter(panel: JcefChatPanel) {

    private val prompting = BridgePrompting(panel)
    private val settings = BridgeSettings(panel)
    private val cards = BridgeCards(panel)
    private val diffs = BridgeDiffs(panel)
    private val attachments = BridgeAttachments(panel)
    private val controls = BridgeSessionControl(panel)
    private val lifecycle = BridgeLifecycle(panel)

    fun dispatch(json: String) {
        when (val m = JcefBridge.parse(json)) {
            is Msg.Prompting -> prompting.handle(m)
            is Msg.Settings -> settings.handle(m)
            is Msg.RequestCard -> cards.handle(m)
            is Msg.Diffs -> diffs.handle(m)
            is Msg.Attachments -> attachments.handle(m)
            is Msg.SessionControl -> controls.handle(m)
            is Msg.Lifecycle -> lifecycle.handle(m)
        }
    }
}
