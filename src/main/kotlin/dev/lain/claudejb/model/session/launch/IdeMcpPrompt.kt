package dev.lain.claudejb.model.session.launch

object IdeMcpPrompt {

    fun text(servers: Set<IdeServer>, rules: Set<IdeRule> = emptySet()): String {
        val parts = listOfNotNull(serversParagraph(servers), ruleLines(rules, servers))
        if (parts.isEmpty()) return ""
        return (listOf(OPEN) + parts + CLOSE).joinToString("\n")
    }

    fun rulesBlock(rules: Set<IdeRule>, servers: Set<IdeServer>): String =
        ruleLines(rules, servers)?.let { listOf(OPEN, it, CLOSE).joinToString("\n") } ?: ""

    private fun serversParagraph(servers: Set<IdeServer>): String? {
        val on = IdeServer.entries.filter { it in servers }
        if (on.isEmpty()) return null
        val listed = on.joinToString(", ") { it.key + " (" + PURPOSE.getValue(it) + ")" }
        return SERVERS + listed + ". " + HOW
    }

    private fun ruleLines(rules: Set<IdeRule>, servers: Set<IdeServer>): String? {
        val active = IdeRule.active(rules, servers)
        if (active.isEmpty()) return null
        val lines = mutableListOf(HEADER)
        var n = 0
        for (server in IdeServer.entries + null) {
            val own = active.filter { it.server == server }
            if (own.isEmpty()) continue
            lines += SERVER_HEADERS.getValue(server)
            own.forEach { lines += "${++n}. ${RULE_TEXT.getValue(it)}" }
        }
        return lines.joinToString("\n")
    }

    const val OPEN = "<ide-integration>"
    const val CLOSE = "</ide-integration>"

    private const val SERVERS = "The IDE is reachable through MCP servers of this plugin's own: "

    private const val HOW = "Each lists only domains(), tools(domain) and run(tool, args): call domains() first, " +
        "load a domain's tools only when a task needs them, and go through the IDE whenever it has the tool " +
        "instead of Read, Grep, Glob, Edit, Write or a shell."

    private val PURPOSE: Map<IdeServer, String> = mapOf(
        IdeServer.CODE to "read, search, navigate, diagnose, edit, refactor and format",
        IdeServer.RUN to "build, run configurations, tests, the terminal and the debugger",
        IdeServer.VCS to "git, and the forge through the IDE's own views",
        IdeServer.OPS to "the Services panel, databases, HTTP, SSH, the project and the IDE itself",
    )

    const val HEADER = "The user's IDE rules override your defaults: their tools go through run(tool, args); never Read, " +
        "Grep, Glob, Edit, Write or Bash, not even outside the project: ours go by socket and cost a fraction. What the " +
        "servers lack is said, never done natively."

    private val SERVER_HEADERS: Map<IdeServer?, String> = mapOf(
        IdeServer.CODE to "code server:",
        IdeServer.RUN to "run server:",
        IdeServer.VCS to "vcs server:",
        IdeServer.OPS to "ops server:",
        null to "Always:",
    )

    private val RULE_TEXT: Map<IdeRule, String> = mapOf(
        IdeRule.CODE_READ to "read_file; file_outline first on big files; list_directory, not ls.",
        IdeRule.CODE_SEARCH to "search_text and find_files to search; find_symbols for symbols; never grep.",
        IdeRule.CODE_NAVIGATE to "definition, references, implementations, symbol_info, hierarchy for symbols.",
        IdeRule.CODE_EDIT to "replace_text and insert_text to edit, create_file for new files, write_file for whole rewrites.",
        IdeRule.CODE_REFACTOR to "rename, move_file, safe_delete to refactor; never by editing text.",
        IdeRule.CODE_FORMAT to "reformat and optimize_imports after editing.",
        IdeRule.CODE_DIAGNOSTICS to "Before done: problems on every touched file, project_problems for the whole; inspect on request.",
        IdeRule.CODE_EDITOR to "open_file what you edit; on indexing errors, index_status and wait.",
        IdeRule.RUN_BUILD to "build, run_tests and tests; never a shell or a script to build or test.",
        IdeRule.RUN_RUN to "run_configuration for what the project already runs; a utility with none gets one in " +
            ".idea/runConfigurations (Tool: <x>); processes to stop.",
        IdeRule.RUN_TERMINAL to "A command goes through shell, never Bash: it runs in the Terminal the user sees.",
        IdeRule.RUN_DEBUG to "session, breakpoint, step, frames, values, not prints; session(stop) when done.",
        IdeRule.VCS_READ to "git_status, git_diff, git_log, git_branches before deciding anything about the tree.",
        IdeRule.VCS_WRITE to "git_stage, git_commit, git_branch, git_remote to stage, commit, branch, sync.",
        IdeRule.VCS_FORGE to "vcs_open and vcs_action for the Log (range=A..B), Commit and Pull Requests views; no gh, no glab.",
        IdeRule.OPS_SERVICES to "services, service_actions, service_action for containers and clusters; never kubectl or docker.",
        IdeRule.OPS_PROJECT to "project, modules, dependencies, dependency_add for structure; plugins for what is installed.",
        IdeRule.OPS_IDE to "tool_window, settings_open, ide_action, notify move the IDE for the user.",
        IdeRule.OPS_DATA to "db_connections, db_schema, db_query; http_files, http_run; ssh_hosts.",
        IdeRule.COMMON_SHOW to "Asked to see or open something: do it in the IDE and say what you opened.",
        IdeRule.COMMON_QUERY to "Questions about the project or the IDE are answered from its tools, never from memory; " +
            "say what you looked at.",
        IdeRule.COMMON_PRS to "Pull requests: list, ask which, open it in the IDE's view, review, report.",
        IdeRule.COMMON_BATCH to "One call carries the whole list: read_file(paths), write_file(files), replace_text(edits), " +
            "search_text(queries). Never one item per call; independent calls go out in one message.",
        IdeRule.COMMON_AGENTS to "Every agent you spawn receives this block verbatim and works the same way, in batches.",
        IdeRule.COMMON_TOOLS to "Scripts serve utilities, never builds or tests; they go under ./.claudetools, /.claudetools/ in " +
            ".gitignore first.",
        IdeRule.COMMON_FALLBACK to "Name a failing server in one line before any fallback, never silently.",
        IdeRule.COMMON_REPORT to "A native tool used while ours existed is the defect: name it and stop.",
    )
}
