package dev.lain.claudejb.model.session.launch

enum class IdeRule(val key: String, val server: IdeServer?, val label: String, val tools: Set<String>) {
    INDEX_READ(
        "index.read",
        IdeServer.INDEX,
        "Read files through the index",
        setOf("ide_read_file", "ide_file_structure", "ide_get_active_file"),
    ),
    INDEX_SEARCH(
        "index.search",
        IdeServer.INDEX,
        "Search through the index",
        setOf("ide_search_text", "ide_find_file", "ide_find_class", "ide_find_symbol"),
    ),
    INDEX_NAVIGATE(
        "index.navigate",
        IdeServer.INDEX,
        "Navigate by symbols, not by text",
        setOf(
            "ide_find_definition",
            "ide_find_references",
            "ide_symbol_info",
            "ide_call_hierarchy",
            "ide_type_hierarchy",
            "ide_find_implementations",
            "ide_find_super_methods",
        ),
    ),
    INDEX_EDIT(
        "index.edit",
        IdeServer.INDEX,
        "Edit through the IDE",
        setOf(
            "ide_replace_text_in_file",
            "ide_replace_member",
            "ide_edit_member",
            "ide_insert_member",
            "ide_create_file",
            "ide_structural_search_replace",
        ),
    ),
    INDEX_REFACTOR(
        "index.refactor",
        IdeServer.INDEX,
        "Refactor with the IDE's engine",
        setOf("ide_refactor_rename", "ide_change_signature", "ide_refactor_safe_delete", "ide_move_file"),
    ),
    INDEX_FORMAT(
        "index.format",
        IdeServer.INDEX,
        "Format with the project's code style",
        setOf("ide_reformat_code", "ide_optimize_imports"),
    ),
    INDEX_BUILD(
        "index.build",
        IdeServer.INDEX,
        "Build and test through the IDE",
        setOf("ide_build_project", "ide_run_tests", "ide_list_tests", "ide_reload_project"),
    ),
    INDEX_DIAGNOSTICS(
        "index.diagnostics",
        IdeServer.INDEX,
        "Check the IDE's problems before calling work done",
        setOf("ide_diagnostics", "ide_project_diagnostics"),
    ),
    INDEX_IDE(
        "index.ide",
        IdeServer.INDEX,
        "Show the user what you touch and mind the IDE's load",
        setOf("ide_open_file", "ide_index_status", "ide_set_project_mode"),
    ),
    INDEX_PLUGINS(
        "index.plugins",
        IdeServer.INDEX,
        "Install and restart the IDE when developing plugins",
        setOf("ide_install_plugin", "ide_restart"),
    ),
    DEBUGGER_RUN(
        "debugger.run",
        IdeServer.DEBUGGER,
        "Run builds, tests and tools as run configurations",
        setOf("list_run_configurations", "execute_run_configuration"),
    ),
    DEBUGGER_DEBUG(
        "debugger.debug",
        IdeServer.DEBUGGER,
        "Debug with breakpoints instead of prints",
        setOf(
            "start_debug_session",
            "set_breakpoint",
            "wait_for_pause",
            "get_debug_session_status",
            "evaluate_expression",
            "get_variables",
            "step_over",
            "stop_debug_session",
        ),
    ),
    DEBUGGER_TESTS(
        "debugger.tests",
        IdeServer.DEBUGGER,
        "Debug a failing test at the assertion",
        setOf("start_debug_session", "set_breakpoint", "wait_for_pause", "list_breakpoints"),
    ),
    DEBUGGER_CONFIGS(
        "debugger.configs",
        IdeServer.DEBUGGER,
        "Configure the IDE with run configurations for repeated commands",
        setOf("list_run_configurations", "execute_run_configuration"),
    ),
    JETBRAINS_PATCH(
        "jetbrains.patch",
        IdeServer.JETBRAINS,
        "Apply multi-file changes as one patch",
        setOf("apply_patch"),
    ),
    JETBRAINS_PROBLEMS(
        "jetbrains.problems",
        IdeServer.JETBRAINS,
        "Read the IDE's inspections and lint",
        setOf("get_file_problems", "lint_files"),
    ),
    JETBRAINS_RUN(
        "jetbrains.run",
        IdeServer.JETBRAINS,
        "Build and run through the IDE",
        setOf("build_project", "get_run_configurations", "execute_run_configuration"),
    ),
    JETBRAINS_VCS(
        "jetbrains.vcs",
        IdeServer.JETBRAINS,
        "Read version control state from the IDE",
        setOf("git_status", "get_repositories"),
    ),
    JETBRAINS_LOGS(
        "jetbrains.logs",
        IdeServer.JETBRAINS,
        "Diagnose the running application from the IDE",
        setOf("get_log_records", "get_spans", "get_services"),
    ),
    JETBRAINS_DB(
        "jetbrains.db",
        IdeServer.JETBRAINS,
        "Query databases through the IDE's connections",
        setOf("list_database_connections", "introspect_schema", "execute_sql_query"),
    ),
    JETBRAINS_XDEBUG(
        "jetbrains.xdebug",
        IdeServer.JETBRAINS,
        "Debug through the IDE's debugger toolset",
        setOf("xdebug_start_debugger_session", "xdebug_set_breakpoint", "xdebug_control_session"),
    ),
    COMMON_AGENTS("common.agents", null, "Agents and subagents inherit these rules", emptySet()),
    COMMON_TOOLS("common.tools", null, "Own tools live under .claudetools, ignored by git", emptySet()),
    COMMON_FALLBACK("common.fallback", null, "A failing server is said out loud, never worked around silently", emptySet()),
    COMMON_REPORT("common.report", null, "Work is done when the IDE reports no new problems", emptySet()),
    ;

    companion object {
        fun of(key: String): IdeRule? = entries.firstOrNull { it.key == key }

        fun forServer(server: IdeServer): List<IdeRule> = entries.filter { it.server == server }

        val common: List<IdeRule> get() = entries.filter { it.server == null }

        fun parse(csv: String): Set<IdeRule> =
            csv.split(',').map { it.trim() }.mapNotNull(::of).toSet()

        fun csv(rules: Collection<IdeRule>): String = rules.sortedBy { it.ordinal }.joinToString(",") { it.key }
    }
}
