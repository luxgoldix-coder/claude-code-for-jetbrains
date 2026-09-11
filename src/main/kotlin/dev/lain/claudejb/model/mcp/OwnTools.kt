package dev.lain.claudejb.model.mcp

import dev.lain.claudejb.model.mcp.toon.Toon
import dev.lain.claudejb.model.mcp.toon.ToonException
import dev.lain.claudejb.model.mcp.toon.ToonOptions
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path

object OwnTools {

    class Call(val server: String, val meta: String, val argument: String?)

    class Review(val toolName: String, val input: JsonObject)

    const val TOOL_USE_ID_KEY = "claudecode/toolUseId"
    const val READ_FILE = "read_file"

    private val META_TOOL = Regex("^mcp__([a-z]+)__(domains|tools|run)$")
    private val EDITS = setOf("replace_text", "insert_text", "create_file", "write_file")

    fun parse(toolName: String, input: JsonObject): Call? {
        val (server, meta) = META_TOOL.matchEntire(toolName)?.destructured ?: return null
        val argument = when (meta) {
            MetaTools.RUN.name -> text(input, "tool")
            MetaTools.TOOLS.name -> text(input, "domain")
            else -> null
        }
        return Call(server, meta, argument)
    }

    fun isOwn(toolName: String?): Boolean = toolName != null && META_TOOL.matches(toolName)

    fun label(call: Call, input: JsonObject = JsonObject(emptyMap())): String = when (call.meta) {
        MetaTools.RUN.name -> call.server + " ▸ " + (call.argument ?: "?") + (path(input)?.let { " ▸ $it" } ?: "")
        MetaTools.TOOLS.name -> call.server + " ▸ tools(" + (call.argument ?: "?") + ")"
        else -> call.server + " ▸ domains"
    }

    fun path(input: JsonObject): String? = args(input)?.let { text(it, "path") }

    fun isEdit(call: Call): Boolean = call.meta == MetaTools.RUN.name && call.argument in EDITS

    fun isRead(call: Call): Boolean = call.meta == MetaTools.RUN.name && call.argument == READ_FILE

    fun argsToon(input: JsonObject): String? = args(input)?.let { Toon.encode(it) }

    fun reviewAs(call: Call, input: JsonObject, projectRoot: String?): Review? {
        if (!isEdit(call)) return null
        val args = args(input) ?: return null
        val path = text(args, "path") ?: return null
        val absolute = Path.of(path).let { if (it.isAbsolute || projectRoot == null) it else Path.of(projectRoot).resolve(it) }
        val reviewed = buildJsonObject {
            put("file_path", absolute.normalize().toString())
            args.filterKeys { it != "path" }.forEach { (key, value) -> put(key, value) }
        }
        val kind = when (call.argument) {
            "replace_text" -> "Edit"
            "insert_text" -> INSERT
            else -> "Write"
        }
        return Review(kind, reviewed)
    }

    const val INSERT = "InsertText"

    fun decodeResult(text: String): JsonElement? = try {
        Toon.decode(text, ToonOptions(strict = false))
    } catch (ignored: ToonException) {
        null
    }

    fun readText(decoded: JsonElement?): String? = (decoded as? JsonObject)?.let { text(it, "text") }

    fun insertedText(input: JsonObject, before: String): String? {
        val line = (input["line"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
        return runCatching { TextEdit.insertAt(before, line, text(input, "content") ?: "").text }.getOrNull()
    }

    private fun args(input: JsonObject): JsonObject? = (input["args"] as? JsonObject)?.takeIf { it.isNotEmpty() }

    private fun text(input: JsonObject, key: String): String? = (input[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
