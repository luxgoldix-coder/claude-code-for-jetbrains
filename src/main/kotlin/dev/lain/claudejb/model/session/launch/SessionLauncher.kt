package dev.lain.claudejb.model.session.launch

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import dev.lain.claudejb.util.InstalledPlugins
import dev.lain.claudejb.util.thisLogger
import java.io.File

object SessionLauncher {

    private val log = thisLogger()

    fun binaryPermissionMode(mode: String): String =
        if (mode == "acceptEdits" || mode == "bypassPermissions") "default" else mode

    fun buildArgs(opts: LaunchOptions, resume: Boolean, mcpConfig: String?): List<String> {
        val args = mutableListOf(
            "--print",
            "--output-format", "stream-json",
            "--input-format", "stream-json",
            "--verbose",
            "--permission-prompt-tool", "stdio",
            "--permission-mode", binaryPermissionMode(opts.permissionMode),
        )
        args += transportFlags(opts)
        args += modelFlags(opts)
        args += toolFilterFlags(opts)
        args += advancedFlags(opts)
        args += appendSystemPromptFlags(systemPrompt(opts))
        mcpConfig?.let { args += listOf("--mcp-config", it) }
        if (resume) {
            opts.sessionId?.let { args += listOf("--resume", it) }
            if (opts.fork && opts.sessionId != null) args += "--fork-session"
        }
        return args
    }

    private fun transportFlags(opts: LaunchOptions): List<String> = buildList {
        if (opts.includePartialMessages) add("--include-partial-messages")
        if (opts.settingSources.isNotBlank()) addAll(listOf("--setting-sources", opts.settingSources))
    }

    private fun modelFlags(opts: LaunchOptions): List<String> = buildList {
        opts.model?.let { addAll(listOf("--model", it)) }
        opts.effort?.let { addAll(listOf("--effort", it)) }
        if (opts.thinkingTokens != null) addAll(listOf("--thinking", "adaptive", "--thinking-display", "summarized"))
    }

    private fun toolFilterFlags(opts: LaunchOptions): List<String> = buildList {
        opts.allowedTools.trim().ifBlank { null }?.let { addAll(listOf("--allowedTools", it)) }
        opts.disallowedTools.trim().ifBlank { null }?.let { addAll(listOf("--disallowedTools", it)) }
    }

    fun appendSystemPromptFlags(prompt: String): List<String> =
        prompt.trim().ifBlank { null }?.let { listOf("--append-system-prompt", it) } ?: emptyList()

    fun systemPrompt(opts: LaunchOptions): String =
        listOf(PluginContextPrompt.TEXT, IdeMcpPrompt.text(ownSockets(opts).keys + ideServers(opts), opts.ideRules, opts.knownIdeTools))
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

    fun rulesBlock(opts: LaunchOptions): String = IdeMcpPrompt.rulesBlock(opts.ideRules, ideServers(opts), opts.knownIdeTools)

    fun ownSockets(opts: LaunchOptions): Map<IdeServer, String> =
        if (opts.ideIntegration) opts.ideSockets.filterKeys { it.own } else emptyMap()

    fun ideServers(opts: LaunchOptions): Set<IdeServer> = buildSet {
        if (opts.ideMcpEnabled) add(IdeServer.JETBRAINS)
        if (opts.indexMcpEnabled) add(IdeServer.INDEX)
        if (opts.debuggerMcpEnabled) add(IdeServer.DEBUGGER)
    }

    private fun hechtcarmelServers(opts: LaunchOptions): Map<IdeServer, Int> = buildMap {
        if (opts.indexMcpEnabled) put(IdeServer.INDEX, opts.indexMcpPort)
        if (opts.debuggerMcpEnabled) put(IdeServer.DEBUGGER, opts.debuggerMcpPort)
    }

    private fun advancedFlags(opts: LaunchOptions): List<String> = buildList {
        opts.maxTurns?.let { addAll(listOf("--max-turns", it.toString())) }
        opts.maxBudgetUsd?.let { addAll(listOf("--max-budget-usd", it.toString())) }
        opts.fallbackModel?.trim()?.ifBlank { null }?.let { addAll(listOf("--fallback-model", it)) }
        for (dir in opts.addDirs) dir.trim().ifBlank { null }?.let { addAll(listOf("--add-dir", it)) }
        opts.betas?.trim()?.ifBlank { null }?.let { addAll(listOf("--betas", it)) }
        if (opts.strictMcpConfig) add("--strict-mcp-config")
    }

    fun mcpConfigJson(opts: LaunchOptions, helper: McpConfigBuilder.HelperParams? = resolveHelper()): String? =
        McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = opts.ideMcpEnabled,
            transport = opts.ideMcpTransport,
            port = opts.ideMcpPort,
            customMcpServers = opts.customMcpServers,
            stdioParams = if (opts.ideMcpEnabled && opts.ideMcpTransport == "stdio") resolveStdioParams(opts) else null,
            onCustomParseError = { log.debug { "Failed to parse custom MCP servers JSON: $it" } },
            ownSockets = ownSockets(opts),
            helper = helper,
            hechtcarmelServers = hechtcarmelServers(opts),
        )

    fun resolveStdioParams(opts: LaunchOptions): McpConfigBuilder.StdioParams? {
        if (!InstalledPlugins.isEnabled(IdeServer.JETBRAINS.plugin)) return null
        val pluginLib = findMcpServerLib() ?: return null
        return McpConfigBuilder.StdioParams(javaBin(), pluginLib, PathManager.getLibPath(), opts.ideMcpPort)
    }

    fun resolveHelper(): McpConfigBuilder.HelperParams? {
        val lib = PluginManager.getPluginByClass(McpConfigBuilder::class.java)?.pluginPath?.resolve("lib")?.toFile()
        if (lib == null || !lib.isDirectory) return null
        return McpConfigBuilder.HelperParams(javaBin(), lib)
    }

    private fun javaBin(): File =
        File(File(System.getProperty("java.home"), "bin"), if (SystemInfo.isWindows) "java.exe" else "java")

    fun findMcpServerLib(): File? {
        val names = listOf("mcpServer", "mcp-server", "MCP Server")
        val roots = listOfNotNull(
            runCatching { java.nio.file.Paths.get(PathManager.getPluginsPath()) }.getOrNull(),
            runCatching { java.nio.file.Paths.get(PathManager.getPreInstalledPluginsPath()) }.getOrNull(),
        )
        for (root in roots) {
            for (name in names) {
                val lib = root.resolve(name).resolve("lib")
                if (java.nio.file.Files.isDirectory(lib)) return lib.toFile()
            }
        }
        return null
    }
}
