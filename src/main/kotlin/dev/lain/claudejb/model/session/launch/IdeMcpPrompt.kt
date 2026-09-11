package dev.lain.claudejb.model.session.launch

object IdeMcpPrompt {

    fun text(rules: Set<IdeRule>, servers: Set<IdeServer>, knownTools: Set<String> = emptySet()): String {
        val active = IdeRule.active(rules, servers).filter { rule ->
            knownTools.isEmpty() || rule.tools.isEmpty() || rule.tools.any { it in knownTools }
        }
        if (active.isEmpty()) return ""
        val lines = mutableListOf(OPEN, HEADER)
        var n = 0
        for (server in IdeServer.entries + null) {
            val own = active.filter { it.server == server }
            if (own.isEmpty()) continue
            lines += SERVER_HEADERS.getValue(server)
            own.forEach { lines += "${++n}. ${RULE_TEXT.getValue(it)}" }
        }
        lines += CLOSE
        return lines.joinToString("\n")
    }

    const val OPEN = "<ide-integration>"
    const val CLOSE = "</ide-integration>"

    const val HEADER = "IDE rules the user enabled; they override your defaults. Where a rule names IDE tools, " +
        "use them, not Read, Grep, Glob, Edit, Write or a shell. If a named tool is missing or errors, say so " +
        "in one line, then fall back."

    private val SERVER_HEADERS: Map<IdeServer?, String> = mapOf(
        IdeServer.INDEX to "Index server (ide_*), the IDE's resolved index:",
        IdeServer.DEBUGGER to "Debugger server:",
        IdeServer.JETBRAINS to "JetBrains server:",
        null to "Always:",
    )

    private val RULE_TEXT: Map<IdeRule, String> = mapOf(
        IdeRule.INDEX_READ to "Read with ide_read_file; ide_file_structure first on big files.",
        IdeRule.INDEX_SEARCH to "Search with ide_search_text, ide_find_file, ide_find_class, ide_find_symbol.",
        IdeRule.INDEX_NAVIGATE to "Resolve symbols with ide_find_definition, ide_find_references, ide_symbol_info, " +
            "ide_call_hierarchy.",
        IdeRule.INDEX_EDIT to "Edit with ide_replace_text_in_file, ide_edit_member, ide_insert_member, ide_create_file.",
        IdeRule.INDEX_REFACTOR to "Rename, move, delete, change signatures with ide_refactor_rename, ide_move_file, " +
            "ide_refactor_safe_delete, ide_change_signature.",
        IdeRule.INDEX_FORMAT to "Format with ide_reformat_code and ide_optimize_imports.",
        IdeRule.INDEX_BUILD to "Build and test with ide_build_project and ide_run_tests; poll the id they return.",
        IdeRule.INDEX_DIAGNOSTICS to "Before calling work done: ide_diagnostics on every touched file, no new problems.",
        IdeRule.INDEX_IDE to "Open what you edit with ide_open_file; on indexing errors, ide_index_status and wait.",
        IdeRule.INDEX_PLUGINS to "Plugin builds: ide_install_plugin, then ide_restart.",
        IdeRule.DEBUGGER_RUN to "Run builds, tests and tools with execute_run_configuration (mode run); no stdout " +
            "comes back, so the configuration writes a log you read.",
        IdeRule.DEBUGGER_DEBUG to "Debug with start_debug_session, set_breakpoint, wait_for_pause, get_debug_session_status, " +
            "evaluate_expression, not prints; stop_debug_session when done.",
        IdeRule.DEBUGGER_TESTS to "A failing test: start_debug_session on its configuration, set_breakpoint at the " +
            "assertion; no guessing from the trace.",
        IdeRule.DEBUGGER_CONFIGS to "A command you repeat becomes a run configuration in .idea/runConfigurations " +
            "(name Tool: <x>), run by execute_run_configuration.",
        IdeRule.JETBRAINS_PATCH to "Multi-file changes: one apply_patch.",
        IdeRule.JETBRAINS_PROBLEMS to "get_file_problems and lint_files after editing.",
        IdeRule.JETBRAINS_RUN to "build_project and execute_run_configuration replace shell builds; " +
            "execute_terminal_command only when no server has the tool.",
        IdeRule.JETBRAINS_VCS to "git_status and get_repositories before committing; the commit stays in Bash.",
        IdeRule.JETBRAINS_LOGS to "Diagnose the running app with get_log_records, get_spans, get_services.",
        IdeRule.JETBRAINS_DB to "Databases: list_database_connections, introspect_schema, execute_sql_query.",
        IdeRule.JETBRAINS_XDEBUG to "Without a Debugger server, debug with xdebug_start_debugger_session and xdebug_*.",
        IdeRule.COMMON_AGENTS to "Every agent or subagent you spawn receives this block verbatim.",
        IdeRule.COMMON_TOOLS to "Own scripts live under ./.claudetools; /.claudetools/ goes in .gitignore first.",
        IdeRule.COMMON_FALLBACK to "Name a failing server in one line before any fallback, never silently.",
        IdeRule.COMMON_REPORT to "A native tool used while its IDE replacement existed is a defect: name it.",
    )
}
