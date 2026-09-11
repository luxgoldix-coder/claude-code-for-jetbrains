package dev.lain.claudejb.model.session.launch

object IdeMcpPrompt {

    fun text(servers: Set<IdeServer>): String {
        val own = IdeServer.OWN.filter { it in servers }
        if (own.isEmpty()) return ""
        val listed = own.joinToString(", ") { it.key + " (" + PURPOSE.getValue(it) + ")" }
        return listOf(OPEN, SERVERS + listed + ". " + HOW + " " + ALWAYS, CLOSE).joinToString("\n")
    }

    const val OPEN = "<ide-integration>"
    const val CLOSE = "</ide-integration>"

    private const val SERVERS = "The IDE is reachable through MCP servers of this plugin's own: "

    private const val HOW = "Each lists only domains(), tools(domain) and run(tool, args): call domains() first, " +
        "load a domain's tools only when a task needs them, and go through the IDE whenever it has the tool " +
        "instead of Read, Grep, Glob, Edit, Write or a shell."

    private const val ALWAYS = "Every agent or subagent you spawn receives this block verbatim. A server that fails " +
        "is named in one line before any fallback, never worked around silently."

    private val PURPOSE: Map<IdeServer, String> = mapOf(
        IdeServer.CODE to "read, search, navigate, diagnose, edit, refactor and format",
        IdeServer.RUN to "build, run configurations, tests, the terminal and the debugger",
        IdeServer.VCS to "git, and the forge through the IDE's own views",
        IdeServer.OPS to "the Services panel, databases, HTTP, SSH, the project and the IDE itself",
    )
}
