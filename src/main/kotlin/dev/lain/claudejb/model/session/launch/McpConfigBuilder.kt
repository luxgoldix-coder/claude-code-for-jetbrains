package dev.lain.claudejb.model.session.launch

import dev.lain.claudejb.model.protocol.ClaudeJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

object McpConfigBuilder {

    data class StdioParams(
        val javaBin: File,
        val pluginLib: File,
        val platformLib: String,
        val port: Int,
    )

    data class HelperParams(val javaBin: File, val lib: File)

    fun mcpConfigJson(
        ideMcpEnabled: Boolean,
        transport: String,
        port: Int,
        customMcpServers: String,
        stdioParams: StdioParams? = null,
        onCustomParseError: (Throwable) -> Unit = {},
        ownSockets: Map<IdeServer, String> = emptyMap(),
        helper: HelperParams? = null,
        hechtcarmelServers: Map<IdeServer, Int> = emptyMap(),
    ): String? {
        val servers = buildJsonObject {
            if (ideMcpEnabled) jetbrainsMcpServer(transport, port, stdioParams)?.let { put(IdeServer.JETBRAINS.mcpName, it) }
            hechtcarmelServers.forEach { (server, serverPort) ->
                server.streamableHttpUrl(serverPort)?.let { put(server.mcpName, httpMcpServer("streamable-http", it)) }
            }
            if (helper != null) ownSockets.forEach { (server, socket) -> put(server.mcpName, ownMcpServer(helper, socket)) }
            customMcpServersObject(customMcpServers, onCustomParseError)?.forEach { (name, server) -> put(name, server) }
        }
        if (servers.isEmpty()) return null
        return buildJsonObject { put("mcpServers", servers) }.toString()
    }

    fun ownMcpServer(helper: HelperParams, socket: String): JsonObject = buildJsonObject {
        put("type", "stdio")
        put("command", helper.javaBin.absolutePath)
        putJsonArray("args") {
            add("-cp")
            add(helper.lib.absolutePath + File.separator + "*")
            add(HELPER_MAIN)
            add(socket)
        }
    }

    const val HELPER_MAIN = "dev.lain.claudejb.mcp.StdioBridge"

    fun jetbrainsMcpServer(transport: String, port: Int, stdioParams: StdioParams?): JsonObject? = when (transport) {
        "stdio" -> stdioParams?.let { stdioMcpServer(it) }
        "streamable-http" -> httpMcpServer("streamable-http", "http://127.0.0.1:$port/stream")
        else -> httpMcpServer("sse", "http://127.0.0.1:$port/sse")
    }

    fun httpMcpServer(type: String, url: String): JsonObject = buildJsonObject {
        put("type", type)
        put("url", url)
        putJsonObject("headers") {}
    }

    fun stdioMcpServer(p: StdioParams): JsonObject? {
        if (!p.javaBin.exists() || !p.pluginLib.isDirectory) return null
        val sep = File.pathSeparator
        val classpath = "${p.pluginLib.absolutePath}${File.separator}*$sep${p.platformLib}${File.separator}*"
        return buildJsonObject {
            put("type", "stdio")
            put("command", p.javaBin.absolutePath)
            putJsonArray("args") {
                add("-classpath")
                add(classpath)
                add("com.intellij.mcpserver.stdio.McpStdioRunnerKt")
            }
            putJsonObject("env") { put("IJ_MCP_SERVER_PORT", p.port.toString()) }
        }
    }

    fun customMcpServersObject(customMcpServers: String, onParseError: (Throwable) -> Unit = {}): JsonObject? {
        val text = customMcpServers.trim().ifBlank { null } ?: return null
        return runCatching { ClaudeJson.parseToJsonElement(text) }
            .onFailure(onParseError)
            .getOrNull() as? JsonObject
    }
}
