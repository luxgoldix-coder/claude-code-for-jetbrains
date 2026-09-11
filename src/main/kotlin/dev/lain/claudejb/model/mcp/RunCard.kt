package dev.lain.claudejb.model.mcp

import dev.lain.claudejb.model.mcp.toon.Toon
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class RunCard(val title: String, val summary: String) {

    companion object {

        private val META_TOOL = Regex("^mcp__([a-z]+)__(domains|tools|run)$")

        fun of(toolName: String, input: JsonObject): RunCard? {
            val (server, meta) = META_TOOL.matchEntire(toolName)?.destructured ?: return null
            return when (meta) {
                MetaTools.RUN.name -> run(server, input)
                MetaTools.TOOLS.name -> tools(server, input)
                else -> RunCard("Claude asks the $server server for its domains", "")
            }
        }

        private fun tools(server: String, input: JsonObject): RunCard {
            val domain = text(input, "domain")
            return RunCard("Claude asks the $server server for its $domain tools", "")
        }

        private fun run(server: String, input: JsonObject): RunCard {
            val tool = text(input, "tool")
            val args = input["args"] as? JsonObject
            return RunCard("Claude wants to use $tool on the $server server", args?.let { Toon.encode(it) }.orEmpty())
        }

        private fun text(input: JsonObject, key: String): String = (input[key] as? JsonPrimitive)?.content ?: "?"
    }
}
