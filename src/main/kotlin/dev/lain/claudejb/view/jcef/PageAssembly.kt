package dev.lain.claudejb.ui.jcef

import dev.lain.claudejb.util.logger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal class Page(val html: String, val headers: Map<String, String>)

internal object PageAssembly {

    private val log = logger<PageAssembly>()

    val appNames = listOf(
        "core/core.js",
        "core/menus.js",
        "core/report.js",
        "core/markdown.js",
        "core/diagram.js",
        "core/diagram-view.js",
        "core/theme.js",
        "transcript/base.js",
        "transcript/rows.js",
        "transcript/guard-notices.js",
        "transcript/tools.js",
        "transcript/output.js",
        "transcript/links.js",
        "transcript/find.js",
        "transcript/find-bar.js",
        "transcript/scroll.js",
        "transcript/trim.js",
        "transcript/transcript.js",
        "composer/base.js",
        "composer/overflow-fit.js",
        "composer/overflow.js",
        "composer/menus.js",
        "composer/pills.js",
        "composer/attach/glyphs.js",
        "composer/attach/tree.js",
        "composer/attach/tree-view.js",
        "composer/attach/tree-select.js",
        "composer/attach/tree-keys.js",
        "composer/attach/menu.js",
        "composer/attach/chips.js",
        "composer/attach/images.js",
        "composer/readout.js",
        "composer/readout-mini.js",
        "composer/palette/rank.js",
        "composer/palette/list.js",
        "composer/palette/palette.js",
        "composer/boot.js",
        "composer/auth.js",
        "composer/actions.js",
        "composer/settings/data.js",
        "composer/settings/rows.js",
        "composer/settings/settings.js",
        "composer/toggles.js",
        "composer/send.js",
        "composer/composer.js",
        "permissions/base.js",
        "permissions/question.js",
        "permissions/elicit.js",
        "permissions/tool.js",
        "permissions/permissions.js",
        "dashboard/base.js",
        "dashboard/cards-usage.js",
        "dashboard/cards.js",
        "dashboard/mcp.js",
        "dashboard/workloads/tree.js",
        "dashboard/workloads/workloads.js",
        "dashboard/git/git.js",
        "dashboard/git/actions.js",
        "dashboard/git/lanes.js",
        "dashboard/git/history.js",
        "dashboard/git/chat.js",
        "dashboard/guard/base.js",
        "dashboard/guard/filters.js",
        "dashboard/guard/entries.js",
        "dashboard/guard/guard.js",
        "dashboard/vuln/base.js",
        "dashboard/vuln/status.js",
        "dashboard/vuln/findings.js",
        "dashboard/vuln/vuln.js",
        "dashboard/log/base.js",
        "dashboard/log/entries.js",
        "dashboard/log/log.js",
        "dashboard/state.js",
        "dashboard/reconcile.js",
        "dashboard/render.js",
        "dashboard/toggles.js",
        "dashboard/dashboard.js",
        "tabs/base.js",
        "tabs/guard.js",
        "tabs/pill.js",
        "tabs/scroll.js",
        "tabs/tabs.js",
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
        "log-view.css",
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
            log.warn("Claude Code chat page is missing declared scripts, so parts of the UI cannot exist: $absent")
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
