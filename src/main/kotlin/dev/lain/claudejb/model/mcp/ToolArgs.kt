package dev.lain.claudejb.model.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ToolException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ToolArgs(val json: JsonObject) {

    fun string(key: String): String = optionalString(key) ?: throw ToolException("missing argument: $key")

    fun optionalString(key: String): String? {
        val value = json[key] ?: return null
        val primitive = value as? JsonPrimitive ?: throw ToolException("argument $key must be a string")
        return primitive.content
    }

    fun int(key: String, default: Int): Int {
        val raw = optionalString(key) ?: return default
        return raw.toIntOrNull() ?: throw ToolException("argument $key must be an integer")
    }

    fun boolean(key: String, default: Boolean): Boolean = when (optionalString(key)) {
        null -> default
        "true" -> true
        "false" -> false
        else -> throw ToolException("argument $key must be a boolean")
    }
}
