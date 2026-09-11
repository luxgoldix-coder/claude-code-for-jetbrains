package dev.lain.claudejb.ui.jcef

import com.intellij.openapi.diagnostic.logger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal class Page(val html: String, val headers: Map<String, String>)

internal object PageAssembly {

    private val log = logger<PageAssembly>()

    val appNames = listOf(
        "app-core.js",
        "app-core-menus.js",
        "app-core-report.js",
        "app-core-markdown.js",
        "app-core-diagram.js",
        "app-core-diagram-view.js",
        "app-core-theme.js",
        "app-transcript-base.js",
        "app-transcript-rows.js",
        "app-transcript-guard-notices.js",
        "app-transcript-tools.js",
        "app-transcript-output.js",
        "app-transcript-links.js",
        "app-transcript-find.js",
        "app-transcript-find-bar.js",
        "app-transcript-scroll.js",
        "app-transcript-trim.js",
        "app-transcript.js",
        "app-composer-base.js",
        "app-composer-overflow-fit.js",
        "app-composer-overflow.js",
        "app-composer-menus.js",
        "app-composer-pills.js",
        "app-composer-attach-glyphs.js",
        "app-composer-attach-tree.js",
        "app-composer-attach-tree-view.js",
        "app-composer-attach-tree-select.js",
        "app-composer-attach-tree-keys.js",
        "app-composer-attach-menu.js",
        "app-composer-attach-chips.js",
        "app-composer-attach-images.js",
        "app-composer-readout.js",
        "app-composer-readout-mini.js",
        "app-composer-palette-rank.js",
        "app-composer-palette-list.js",
        "app-composer-palette.js",
        "app-composer-boot.js",
        "app-composer-auth.js",
        "app-composer-actions.js",
        "app-composer-settings-data.js",
        "app-composer-settings-rows.js",
        "app-composer-settings.js",
        "app-composer-toggles.js",
        "app-composer-send.js",
        "app-composer.js",
        "app-permissions-base.js",
        "app-permissions-question.js",
        "app-permissions-elicit.js",
        "app-permissions-tool.js",
        "app-permissions.js",
        "app-session-base.js",
        "app-session-cards-usage.js",
        "app-session-cards.js",
        "app-session-mcp.js",
        "app-session-workloads-tree.js",
        "app-session-workloads.js",
        "app-session-git.js",
        "app-session-git-actions.js",
        "app-session-git-lanes.js",
        "app-session-git-history.js",
        "app-session-gitchat.js",
        "app-session-guard-base.js",
        "app-session-guard-filters.js",
        "app-session-guard-entries.js",
        "app-session-guard.js",
        "app-session-vuln-base.js",
        "app-session-vuln-status.js",
        "app-session-vuln-findings.js",
        "app-session-vuln.js",
        "app-session-state.js",
        "app-session-reconcile.js",
        "app-session-render.js",
        "app-session-toggles.js",
        "app-session.js",
        "app-tabs-base.js",
        "app-tabs-guard.js",
        "app-tabs-pill.js",
        "app-tabs-scroll.js",
        "app-tabs.js",
    )

    val CSS_PARTS = listOf(
        "base.css",
        "transcript-rows.css",
        "transcript-markdown.css",
        "transcript-fold.css",
        "transcript-tools.css",
        "transcript-code.css",
        "transcript-notices.css",
        "composer-card.css",
        "composer-bar.css",
        "composer-attach.css",
        "composer-readout.css",
        "composer-palette.css",
        "permissions-cards.css",
        "permissions-elicit.css",
        "attachments.css",
        "find-bar.css",
        "dashboard-panel.css",
        "dashboard-cards.css",
        "dashboard-diagram.css",
        "dashboard-mini.css",
        "git-view.css",
        "git-history.css",
        "git-chat.css",
        "guard-log.css",
        "guard-filters.css",
        "vuln-status.css",
        "vuln-findings.css",
        "boot-screen.css",
        "auth.css",
        "vibe.css",
        "focus.css",
        "tabs-bar.css",
        "tabs-pills.css",
    )

    private val LIB_NAMES = listOf("purify.min.js", "marked.min.js", "highlight.min.js")

    fun build(): Page {
        val shell = readResource("shell.html")
            ?: return Page(FALLBACK_HTML, headersFor(cspWith("'none'", "'none'")))

        val styleInner = "\n" + CSS_PARTS.joinToString("\n") { readResource("css/$it").orEmpty() } + "\n"
        val styleSrc = "'sha256-" + sha256Base64(styleInner) + "'"

        val contents = LinkedHashMap<String, String>()
        (LIB_NAMES + appNames).forEach { name -> readResource(name)?.let { contents[name] = it } }

        val absent = (LIB_NAMES + appNames).filterNot { contents.containsKey(it) }
        if (absent.isNotEmpty()) {
            log.error("Claude Code chat page is missing declared scripts, so parts of the UI cannot exist: $absent")
        }

        val hashes = contents.values.map { "'sha256-" + sha256Base64(it) + "'" }
        val scriptSrc = if (hashes.isEmpty()) "'none'" else hashes.joinToString(" ")
        val csp = cspWith(scriptSrc, styleSrc)

        fun block(names: List<String>): String =
            names.filter { contents.containsKey(it) }.joinToString("\n") { "<script>${contents[it]}</script>" }

        val html = shell
            .replace("<!--CSP-->", "<meta http-equiv=\"Content-Security-Policy\" content=\"$csp\">")
            .replace("<!--CSS-->", "<style>$styleInner</style>")
            .replace("<!--LIBS-->", block(LIB_NAMES))
            .replace("<!--APP-->", block(appNames))

        return Page(html, headersFor(csp))
    }

    private fun readResource(name: String): String? =
        PageAssembly::class.java.getResourceAsStream("/jcef/$name")?.use { stream ->
            stream.readBytes().toString(StandardCharsets.UTF_8)
        }

    private const val PERMISSIONS_POLICY =
        "accelerometer=(), autoplay=(), camera=(), clipboard-read=(), clipboard-write=(), " +
            "display-capture=(), encrypted-media=(), fullscreen=(), geolocation=(), gyroscope=(), " +
            "magnetometer=(), microphone=(), midi=(), payment=(), usb=(), xr-spatial-tracking=()"

    private fun cspWith(scriptSrc: String, styleSrc: String): String =
        "default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; " +
            "object-src 'none'; img-src data:; font-src 'none'; connect-src 'none'; " +
            "style-src $styleSrc; script-src $scriptSrc; upgrade-insecure-requests"

    private val BASE_HEADERS = linkedMapOf(
        "Content-Type" to "text/html; charset=utf-8",
        "X-Content-Type-Options" to "nosniff",
        "X-Frame-Options" to "DENY",
        "Referrer-Policy" to "no-referrer",
        "Permissions-Policy" to PERMISSIONS_POLICY,
        "Cross-Origin-Opener-Policy" to "same-origin",
        "Cross-Origin-Embedder-Policy" to "require-corp",
        "Cross-Origin-Resource-Policy" to "same-origin",
        "X-XSS-Protection" to "1; mode=block",
        "Cache-Control" to "no-store, max-age=0",
        "Pragma" to "no-cache",
        "Expires" to "0",
        "Clear-Site-Data" to "\"cookies\", \"storage\"",
    )

    private fun headersFor(csp: String): Map<String, String> =
        LinkedHashMap(BASE_HEADERS).apply { put("Content-Security-Policy", csp) }

    private fun sha256Base64(s: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray(StandardCharsets.UTF_8)),
        )

    private const val FALLBACK_HTML =
        "<!doctype html><html><body style=\"font-family:sans-serif;padding:16px\">" +
            "Claude Code failed to load its UI resources." +
            "</body></html>"
}
