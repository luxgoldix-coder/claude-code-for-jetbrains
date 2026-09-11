package dev.lain.claudejb.ui.jcef

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.Alarm
import dev.lain.claudejb.util.edtNow

internal class PageDelivery(
    private val browser: JBCefBrowser,
    private val page: Page,
    parentDisposable: Disposable,
    private val webReady: () -> Boolean,
    private val onRedeliver: () -> Unit,
    private val isDisposed: () -> Boolean,
) {

    private val log = logger<PageDelivery>()

    private val readyWatchdog = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)

    var route: PageRoute? = null
        private set

    @Volatile
    private var loopback: LoopbackPageServer? = null

    fun start() = deliver(startRoute(schemeAvailable = SchemePageServer.register(page)))

    fun isOwnPage(url: String?): Boolean = isOwnPageUrl(url, SchemePageServer.PAGE_URL, loopback?.url)

    fun cancelWatchdog() = readyWatchdog.cancelAllRequests()

    fun stopLoopback() {
        loopback?.stop()
    }

    fun relaxWatchdog() {
        if (webReady()) return
        readyWatchdog.cancelAllRequests()
        readyWatchdog.addRequest({ if (!webReady()) promote() }, SCRIPTS_WATCHDOG_MS)
    }

    private fun startRoute(schemeAvailable: Boolean): PageRoute =
        maxOf(if (schemeAvailable) PageRoute.SCHEME else PageRoute.LOOPBACK, provenRoute)

    private fun deliver(next: PageRoute) {
        route = next
        if (next != PageRoute.NOTICE) armWatchdog()
        when (next) {
            PageRoute.SCHEME -> browser.loadURL(SchemePageServer.PAGE_URL)
            PageRoute.LOOPBACK -> serveOverLoopback()
            PageRoute.INLINE -> browser.loadHTML(page.html)
            PageRoute.NOTICE -> loopback?.let { browser.loadHTML(RemoteDevNotice.html(it.port)) }
        }
    }

    private fun armWatchdog() {
        if (webReady()) return
        readyWatchdog.cancelAllRequests()
        readyWatchdog.addRequest({ if (!webReady()) promote() }, READY_WATCHDOG_MS)
    }

    private fun promote() {
        val current = route ?: return
        val next = nextPageRoute(current, loopbackBound = loopback != null)
        if (next == null) {
            log.warn("Claude Code chat did not come up over $current and there is no route left to try")
            return
        }
        onRedeliver()
        log.warn("Claude Code chat did not come up over $current — delivering it over $next instead")
        if (next == PageRoute.LOOPBACK) provenRoute = PageRoute.LOOPBACK
        deliver(next)
    }

    private fun serveOverLoopback() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val server = LoopbackPageServer.start(page.html, page.headers)
            edtNow {
                if (isDisposed() || route != PageRoute.LOOPBACK) {
                    server?.stop()
                    return@edtNow
                }
                if (server == null) {
                    log.warn("Claude Code could not bind a loopback port for the chat page")
                    promote()
                } else {
                    loopback = server
                    browser.loadURL(server.url)
                }
            }
        }
    }

    private companion object {
        const val READY_WATCHDOG_MS = 2500

        const val SCRIPTS_WATCHDOG_MS = 20_000

        @Volatile
        var provenRoute: PageRoute = PageRoute.SCHEME
    }
}
