package dev.lain.claudejb.model.mcp

import dev.lain.claudejb.model.mcp.toon.Toon
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun interface ToolGate {
    fun denial(tool: ToolSpec, arguments: JsonObject): String?
}

class MetaTools(private val catalog: ToolCatalog, private val gate: ToolGate, private val budget: OutputBudget) {

    val specs: List<ToolSpec> = listOf(DOMAINS, TOOLS, RUN)

    suspend fun call(name: String, arguments: JsonObject): ToolResult? = when (name) {
        DOMAINS.name -> domains()
        TOOLS.name -> tools(ToolArgs(arguments))
        RUN.name -> run(arguments)
        else -> null
    }

    private fun domains(): ToolResult {
        val table = buildJsonObject {
            put(
                "domains",
                buildJsonArray {
                    for (domain in catalog.domains) {
                        add(
                            buildJsonObject {
                                put("name", domain.name)
                                put("description", domain.description)
                            },
                        )
                    }
                },
            )
        }
        return ToolResult(PRIMER + Toon.encode(table))
    }

    private fun tools(args: ToolArgs): ToolResult {
        val name = args.string("domain")
        val domain = catalog.domain(name)
            ?: return ToolResult.error("unknown domain $name; the domains are ${catalog.domains.joinToString { it.name }}")
        val listing = buildJsonObject {
            put(
                "tools",
                buildJsonArray {
                    for (tool in domain.tools) add(describe(tool.spec))
                },
            )
        }
        return ToolResult(Toon.encode(listing))
    }

    private fun describe(spec: ToolSpec): JsonObject = buildJsonObject {
        put("name", spec.name)
        put("description", spec.description)
        if (spec.mutates) put("mutates", true)
        put(
            "params",
            buildJsonArray {
                for (param in spec.params) {
                    add(
                        buildJsonObject {
                            put("name", param.name)
                            put("type", param.type)
                            put("required", param.required)
                            put("description", param.description)
                        },
                    )
                }
            },
        )
    }

    private suspend fun run(arguments: JsonObject): ToolResult {
        val name = ToolArgs(arguments).string("tool")
        val tool = catalog.tool(name) ?: return ToolResult.error("unknown tool $name; call domains() then tools(domain)")
        gate.denial(tool.spec, arguments)?.let { return ToolResult.error(it) }
        val args = arguments["args"]?.let { it as? JsonObject } ?: JsonObject(emptyMap())
        val result = try {
            tool.run(ToolArgs(args))
        } catch (e: ToolException) {
            ToolResult.error(e.message ?: "tool failed")
        }
        return ToolResult(budget.fit(result.text), result.isError)
    }

    companion object {

        val DOMAINS = ToolSpec("domains", "Lists this server's tool domains, one line each. Start here.")

        val TOOLS = ToolSpec(
            "tools",
            "Lists the tools of one domain with their parameters. Call it only for the domain you are about to use.",
            listOf(Param("domain", "A domain name from domains()")),
        )

        val RUN = ToolSpec(
            "run",
            "Runs one tool from tools(domain) with its arguments. Results are TOON.",
            listOf(
                Param("tool", "The tool name exactly as listed by tools(domain)"),
                Param("args", "The tool's arguments as an object", type = "object", required = false),
            ),
            mutates = true,
        )

        const val PRIMER =
            "# TOON: key: value | key[N]: a,b | key[N]{f1,f2}: then one row per line | quotes only when needed\n" +
                "# Next: tools(domain) for one domain's tools, then run(tool, args).\n"
    }
}
