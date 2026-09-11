package dev.lain.claudejb.model.session.launch

import dev.lain.claudejb.model.protocol.ClaudeJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class McpConfigBuilderTest {

    private fun parse(s: String): JsonObject =
        ClaudeJson.parseToJsonElement(s).jsonObject

    private fun servers(json: String): JsonObject =
        parse(json)["mcpServers"]!!.jsonObject

    @Test
    fun `disabled IDE plus blank custom returns null (no mcp-config flag)`() {
        val out = McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = false,
            transport = "sse",
            port = 64342,
            customMcpServers = "",
        )
        assertNull(out)
    }

    @Test
    fun `sse transport synthesizes loopback URL with sse type`() {
        val out = McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = true,
            transport = "sse",
            port = 64342,
            customMcpServers = "",
        )
        assertNotNull(out)
        val jb = servers(out!!)["jetbrains"]!!.jsonObject
        assertEquals("sse", jb["type"]!!.jsonPrimitive.content)
        assertEquals("http://127.0.0.1:64342/sse", jb["url"]!!.jsonPrimitive.content)
        assertNotNull(jb["headers"])
        assertTrue(jb["headers"]!!.jsonObject.isEmpty())
    }

    @Test
    fun `streamable-http transport uses stream endpoint and matching type`() {
        val out = McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = true,
            transport = "streamable-http",
            port = 12345,
            customMcpServers = "",
        )
        val jb = servers(out!!)["jetbrains"]!!.jsonObject
        assertEquals("streamable-http", jb["type"]!!.jsonPrimitive.content)
        assertEquals("http://127.0.0.1:12345/stream", jb["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `unknown transport falls back to sse`() {
        val out = McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = true,
            transport = "garbage",
            port = 7777,
            customMcpServers = "",
        )
        val jb = servers(out!!)["jetbrains"]!!.jsonObject
        assertEquals("sse", jb["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `stdio without StdioParams skips the jetbrains entry`() {
        val out = McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = true,
            transport = "stdio",
            port = 1,
            customMcpServers = "",
            stdioParams = null,
        )
        assertNull(out)
    }

    @Test
    fun `stdio with resolved params emits classpath args and port env`(@TempDir tmp: Path) {
        val javaBin = File(tmp.toFile(), "java").apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }
        val pluginLib = File(tmp.toFile(), "mcpserver/lib").apply { mkdirs() }
        val platformLib = File(tmp.toFile(), "platform/lib").apply { mkdirs() }.absolutePath
        val params = McpConfigBuilder.StdioParams(javaBin, pluginLib, platformLib, port = 4242)

        val out = McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = true,
            transport = "stdio",
            port = 4242,
            customMcpServers = "",
            stdioParams = params,
        )
        val jb = servers(out!!)["jetbrains"]!!.jsonObject
        assertEquals("stdio", jb["type"]!!.jsonPrimitive.content)
        assertEquals(javaBin.absolutePath, jb["command"]!!.jsonPrimitive.content)
        val args = jb["args"]!!.toString()
        assertTrue(args.contains("-classpath"), "args=$args")
        assertTrue(args.contains("McpStdioRunnerKt"), "args=$args")
        val env = jb["env"]!!.jsonObject
        assertEquals("4242", env["IJ_MCP_SERVER_PORT"]!!.jsonPrimitive.content)
    }

    @Test
    fun `custom server only merges under mcpServers without jetbrains key`() {
        val custom = """{"my-srv":{"type":"sse","url":"http://localhost:9000/sse","headers":{}}}"""
        val out = McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = false,
            transport = "sse",
            port = 0,
            customMcpServers = custom,
        )
        val s = servers(out!!)
        assertNull(s["jetbrains"], "no jetbrains key when ideMcpEnabled=false")
        val my = s["my-srv"]!!.jsonObject
        assertEquals("sse", my["type"]!!.jsonPrimitive.content)
        assertEquals("http://localhost:9000/sse", my["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `IDE + custom merge without collision (jetbrains key reserved)`() {
        val custom = """{"linter":{"type":"sse","url":"http://localhost:1/sse","headers":{}}}"""
        val out = McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = true,
            transport = "sse",
            port = 64342,
            customMcpServers = custom,
        )
        val s = servers(out!!)
        assertNotNull(s["jetbrains"])
        assertNotNull(s["linter"])
        assertEquals("http://127.0.0.1:64342/sse", s["jetbrains"]!!.jsonObject["url"]!!.jsonPrimitive.content)
        assertEquals("http://localhost:1/sse", s["linter"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `invalid custom JSON reports via callback and is dropped (no flag, no crash)`() {
        var captured: Throwable? = null
        val out = McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = false,
            transport = "sse",
            port = 0,
            customMcpServers = "{not valid json",
            onCustomParseError = { captured = it },
        )
        assertNotNull(captured, "parse error must be surfaced through the callback")
        assertNull(out, "with no JetBrains server and an unparseable custom block → no flag emitted")
    }

    @Test
    fun `invalid custom JSON alongside enabled IDE still emits only the jetbrains entry`() {
        var captured: Throwable? = null
        val out = McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = true,
            transport = "sse",
            port = 64342,
            customMcpServers = "[]",
            onCustomParseError = { captured = it },
        )
        assertNull(captured)
        val s = servers(out!!)
        assertNotNull(s["jetbrains"])
        assertEquals(1, s.size, "only the JetBrains entry survives a non-object custom block")
    }

    @Test
    fun `our own servers are stdio entries, one per socket, launching the helper by a bare java`(@TempDir tmp: Path) {
        val helper = helper(tmp)
        val out = McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = false,
            transport = "sse",
            port = 0,
            customMcpServers = "",
            ownSockets = mapOf(IdeServer.CODE to "/run/x/code.sock", IdeServer.OPS to "/run/x/ops.sock"),
            helper = helper,
        )
        val s = servers(out!!)
        assertNull(s["jetbrains"])
        assertEquals(listOf("code", "ops"), s.keys.toList())
        val code = s["code"]!!.jsonObject
        assertEquals("stdio", code["type"]!!.jsonPrimitive.content)
        assertEquals(helper.javaBin.absolutePath, code["command"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("-cp", helper.lib.absolutePath + File.separator + "*", McpConfigBuilder.HELPER_MAIN, "/run/x/code.sock"),
            code["args"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertNull(code["env"], "the credential never travels by environment")
        assertEquals("/run/x/ops.sock", s["ops"]!!.jsonObject["args"]!!.jsonArray.last().jsonPrimitive.content)
    }

    @Test
    fun `without a helper to launch, a socket earns no entry`() {
        val out = McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = false,
            transport = "sse",
            port = 0,
            customMcpServers = "",
            ownSockets = mapOf(IdeServer.CODE to "/run/x/code.sock"),
        )
        assertNull(out)
    }

    @Test
    fun `a custom server named like an IDE server wins, as it always did for jetbrains`(@TempDir tmp: Path) {
        val custom = """{"code":{"type":"sse","url":"http://localhost:1/sse","headers":{}}}"""
        val out = McpConfigBuilder.mcpConfigJson(
            ideMcpEnabled = false,
            transport = "sse",
            port = 0,
            customMcpServers = custom,
            ownSockets = mapOf(IdeServer.CODE to "/run/x/code.sock"),
            helper = helper(tmp),
        )
        assertEquals("http://localhost:1/sse", servers(out!!)["code"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }

    private fun helper(tmp: Path): McpConfigBuilder.HelperParams {
        val javaBin = File(tmp.toFile(), "java").apply { writeText("#!/bin/sh\n") }
        val lib = File(tmp.toFile(), "lib").apply { mkdirs() }
        return McpConfigBuilder.HelperParams(javaBin, lib)
    }

    @Test
    fun `customMcpServersObject returns null for blank input`() {
        assertNull(McpConfigBuilder.customMcpServersObject(""))
        assertNull(McpConfigBuilder.customMcpServersObject("   \n"))
    }
}
