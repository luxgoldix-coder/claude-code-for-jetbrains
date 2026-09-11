package dev.lain.claudejb.ui

import dev.lain.claudejb.ui.jcef.Msg

internal class BridgeLog(private val panel: JcefChatPanel) {

    fun handle(m: Msg.Log) {
        when (m) {
            is Msg.LogLines -> panel.logFeed.push(m.since)
            is Msg.LogDebug -> panel.logFeed.setDebug(m.on)
            is Msg.LogCopy -> panel.logFeed.copy(m.level)
        }
    }
}
