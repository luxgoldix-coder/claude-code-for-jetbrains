package dev.lain.claudejb.ui.jcef

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.Alarm
import dev.lain.claudejb.util.edtNow
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.util.LinkedList
import javax.swing.JComponent
import javax.swing.border.EmptyBorder

class JcefHost(
    parentDisposable: Disposable,
    private val onMessage: (String) -> Unit,
) {

    val supported: Boolean = JBCefApp.isSupported()

    private val browser: JBCefBrowser?
    private val jsQuery: JBCefJSQuery?

    private var ready: Boolean = false

    private val pending = LinkedList<String>()

    private var webReady: Boolean = false

    @Volatile
    private var disposed: Boolean = false

    @Volatile
    private var mainFrameLoadFailed: Boolean = false

    private var delivery: PageDelivery? = null

    private val deferred = ArrayList<ReadyBlock>()

    private val deferredAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)

    val component: JComponent

    init {
        if (!supported) {
            browser = null
            jsQuery = null
            component = JBLabel(
                "Claude Code needs JCEF — enable `ide.browser.jcef.enabled` in the Registry and restart.",
            ).apply {
                border = EmptyBorder(16, 16, 16, 16)
            }
        } else {
            val b = JBCefBrowser.createBuilder().build()
            browser = b
            component = b.component

            Disposer.register(parentDisposable, b)

            Disposer.register(
                parentDisposable,
                Disposable {
                    disposed = true
                    delivery?.stopLoopback()
                },
            )

            val base: JBCefBrowserBase = b
            val query = JBCefJSQuery.create(base)
            jsQuery = query
            Disposer.register(parentDisposable, query)
            query.addHandler { request ->
                ApplicationManager.getApplication().invokeLater { onMessage(request) }
                null
            }

            installNavigationGuards(b)
            installLoadHandler(b, query)

            val d = PageDelivery(
                browser = b,
                page = PageAssembly.build(),
                parentDisposable = parentDisposable,
                webReady = { webReady },
                onRedeliver = { ready = false },
                isDisposed = { disposed },
            )
            delivery = d
            d.start()
        }
    }

    fun exec(js: String) {
        val b = browser ?: return
        edtNow {
            if (ready) {
                executeNow(b, js)
            } else {
                pending.add(js)
            }
        }
    }

    fun execBuilt(method: String, build: () -> String?) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val payload = runCatching(build)
                .onFailure { log.warn("Claude Code: $method could not be answered", it) }
                .getOrNull() ?: return@executeOnPooledThread
            if (!disposed) exec("$method && $method($payload)")
        }
    }

    fun whenWebReady(timeoutMs: Long = WEB_READY_TIMEOUT_MS, block: () -> Unit) {
        edtNow {
            if (webReady || browser == null) {
                block()
                return@edtNow
            }
            val entry = ReadyBlock(block)
            deferred.add(entry)
            deferredAlarm.addRequest({ runDeferred(entry) }, timeoutMs)
        }
    }

    fun markWebReady() {
        edtNow {
            webReady = true
            delivery?.cancelWatchdog()
            delivery?.stopLoopback()
            if (inputComponent()?.isFocusOwner == true) grantCefFocus()
            flushDeferred()
        }
    }

    fun requestFocus() {
        edtNow {
            val target = inputComponent() ?: return@edtNow
            if (target.isFocusOwner) grantCefFocus() else IdeFocusManager.getGlobalInstance().requestFocus(target, true)
        }
    }

    private fun grantCefFocus() {
        runCatching { browser?.cefBrowser?.setFocus(true) }
        exec("window.cc.focusInput && window.cc.focusInput()")
    }

    fun inputComponent(): JComponent? {
        val b = browser ?: return null
        return runCatching { b.cefBrowser.uiComponent }.getOrNull() as? JComponent
    }

    fun dispose() {
        edtNow {
            deferredAlarm.cancelAllRequests()
            deferred.clear()
        }
        delivery?.stopLoopback()
        jsQuery?.let { Disposer.dispose(it) }
        browser?.let { Disposer.dispose(it) }
    }

    private class ReadyBlock(val block: () -> Unit)

    private fun flushDeferred() {
        deferredAlarm.cancelAllRequests()
        val queued = ArrayList(deferred)
        deferred.clear()
        queued.forEach { it.block() }
    }

    private fun runDeferred(entry: ReadyBlock) {
        if (!deferred.remove(entry)) return
        log.warn("Claude Code chat page has not announced itself in time — running a deferred action without it")
        entry.block()
    }

    private fun installLoadHandler(b: JBCefBrowser, query: JBCefJSQuery) {
        b.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadStart(
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
                    transitionType: CefRequest.TransitionType?,
                ) {
                    if (frame != null && !frame.isMain) return
                    mainFrameLoadFailed = false
                }

                override fun onLoadError(
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
                    errorCode: CefLoadHandler.ErrorCode?,
                    errorText: String?,
                    failedUrl: String?,
                ) {
                    if (frame != null && !frame.isMain) return
                    mainFrameLoadFailed = true
                }

                override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame != null && !frame.isMain) return
                    if (!pageArrived(httpStatusCode, mainFrameLoadFailed)) {
                        edtNow {
                            log.warn(
                                "Claude Code chat page did not load over ${delivery?.route ?: "an unknown route"} " +
                                    "(http status $httpStatusCode) — keeping the queued state for the next one",
                            )
                        }
                        return
                    }
                    val inject = "window.__ccSend = function(p){ " + query.inject("p") + " };"
                    executeNow(b, inject)
                    edtNow {
                        ready = true
                        delivery?.relaxWatchdog()
                        while (pending.isNotEmpty()) {
                            executeNow(b, pending.poll())
                        }
                    }
                }
            },
            b.cefBrowser,
        )
    }

    private fun installNavigationGuards(b: JBCefBrowser) {
        b.jbCefClient.addRequestHandler(
            object : CefRequestHandlerAdapter() {
                override fun onBeforeBrowse(
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
                    request: CefRequest?,
                    userGesture: Boolean,
                    isRedirect: Boolean,
                ): Boolean = !isOwnPage(request?.url)
            },
            b.cefBrowser,
        )

        b.jbCefClient.addLifeSpanHandler(
            object : CefLifeSpanHandlerAdapter() {
                override fun onBeforePopup(
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
                    targetUrl: String?,
                    targetFrameName: String?,
                ): Boolean = true
            },
            b.cefBrowser,
        )
    }

    private fun isOwnPage(url: String?): Boolean =
        delivery?.isOwnPage(url) ?: isOwnPageUrl(url, SchemePageServer.PAGE_URL, null)

    private fun executeNow(b: JBCefBrowser, js: String) {
        val url = b.cefBrowser.url ?: SchemePageServer.PAGE_URL
        val guarded = "try{" + js + "}catch(e){try{window.__ccSend&&window.__ccSend(JSON.stringify(" +
            "{type:'diag',report:'uncaught exec: '+((e&&e.stack)||e)}))}catch(_){}}"
        b.cefBrowser.executeJavaScript(guarded, url, 0)
    }

    private companion object {
        private val log = logger<JcefHost>()

        private const val WEB_READY_TIMEOUT_MS = 5_000L
    }
}
