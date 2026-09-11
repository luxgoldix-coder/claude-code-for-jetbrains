package dev.lain.claudejb.model.mcp

import dev.lain.claudejb.model.mcp.toon.Toon
import dev.lain.claudejb.model.mcp.toon.ToonException
import dev.lain.claudejb.model.mcp.toon.ToonOptions
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object OwnTools {

    class Call(val server: String, val meta: String, val argument: String?)

    private val META_TOOL = Regex("^mcp__([a-z]+)__(domains|tools|run)$")

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

    fun label(call: Call): String = when (call.meta) {
        MetaTools.RUN.name -> call.server + " ▸ " + (call.argument ?: "?")
        MetaTools.TOOLS.name -> call.server + " ▸ tools(" + (call.argument ?: "?") + ")"
        else -> call.server + " ▸ domains"
    }

    fun argsToon(input: JsonObject): String? = (input["args"] as? JsonObject)?.takeIf { it.isNotEmpty() }?.let { Toon.encode(it) }

    fun decodeResult(text: String): JsonElement? = try {
        Toon.decode(text, ToonOptions(strict = false))
    } catch (ignored: ToonException) {
        null
    }

    private fun text(input: JsonObject, key: String): String? = (input[key] as? JsonPrimitive)?.content
}
