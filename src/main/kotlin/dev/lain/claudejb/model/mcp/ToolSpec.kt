package dev.lain.claudejb.model.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ToolSpec(
    val name: String,
    val description: String,
    val params: List<Param> = emptyList(),
    val mutates: Boolean = false,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    val inputSchema: JsonObject
        get() = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            put(
                "properties",
                buildJsonObject {
                    for (param in params) {
                        put(
                            param.name,
                            buildJsonObject {
                                put("type", param.type)
                                put("description", param.description)
                            },
                        )
                    }
                },
            )
            put("required", buildJsonArray { params.filter { it.required }.forEach { add(JsonPrimitive(it.name)) } })
        }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 120_000L
    }
}

data class Param(val name: String, val description: String, val type: String = "string", val required: Boolean = true)

class Tool(val spec: ToolSpec, val run: suspend (ToolArgs) -> ToolResult)

class ToolDomain(val name: String, val description: String, val tools: List<Tool>) {
    init {
        require(tools.size <= MAX_TOOLS) { "domain $name exposes ${tools.size} tools; the ceiling is $MAX_TOOLS" }
    }

    companion object {
        const val MAX_TOOLS = 4
    }
}
