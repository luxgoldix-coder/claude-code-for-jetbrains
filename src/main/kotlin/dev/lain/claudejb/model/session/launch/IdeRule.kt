package dev.lain.claudejb.model.session.launch

enum class IdeRule(val key: String, val server: IdeServer?, val label: String, val tools: Set<String>) {
    CODE_READ("code.read", IdeServer.CODE, "Read through the index", setOf("read_file", "file_outline", "list_directory")),
    CODE_SEARCH("code.search", IdeServer.CODE, "Search through the index", setOf("search_text", "find_files", "find_symbols")),
    CODE_NAVIGATE(
        "code.navigate",
        IdeServer.CODE,
        "Navigate by symbols, not by text",
        setOf("definition", "references", "implementations", "symbol_info", "hierarchy"),
    ),
    CODE_EDIT("code.edit", IdeServer.CODE, "Edit through the IDE", setOf("replace_text", "insert_text", "create_file", "write_file")),
    CODE_EDIT_OPS("code.edit_ops", IdeServer.CODE, "The Edit menu on a file", setOf("undo", "redo", "search_replace", "line_ops")),
    CODE_REFACTOR("code.refactor", IdeServer.CODE, "Refactor with the IDE's engine", setOf("rename", "move_file", "safe_delete")),
    CODE_FORMAT("code.format", IdeServer.CODE, "Format with the project's code style", setOf("reformat", "optimize_imports")),
    CODE_DIAGNOSTICS(
        "code.diagnostics",
        IdeServer.CODE,
        "No new problems before calling work done",
        setOf("problems", "project_problems", "problems_view", "inspect", "inspections"),
    ),
    CODE_EDITOR(
        "code.editor",
        IdeServer.CODE,
        "Show the user what you touch",
        setOf("open_file", "active_file", "index_status", "editor_action"),
    ),
    RUN_BUILD("run.build", IdeServer.RUN, "Build and test through the IDE", setOf("build", "run_tests", "tests")),
    RUN_RUN(
        "run.run",
        IdeServer.RUN,
        "Run configurations, not commands; create one when the task has none",
        setOf("run_configurations", "run_configuration", "processes"),
    ),
    RUN_TERMINAL("run.terminal", IdeServer.RUN, "Commands run in the IDE's terminal", setOf("shell")),
    RUN_DEBUG(
        "run.debug",
        IdeServer.RUN,
        "Debug with breakpoints instead of prints",
        setOf("session", "step", "frames", "values", "breakpoint"),
    ),
    VCS_READ("vcs.read", IdeServer.VCS, "Git through the IDE", setOf("git_status", "git_diff", "git_log", "git_branches")),
    VCS_WRITE("vcs.write", IdeServer.VCS, "Commit through the IDE", setOf("git_stage", "git_commit", "git_branch", "git_remote")),
    VCS_FORGE("vcs.forge", IdeServer.VCS, "The forge through the IDE's views", setOf("vcs_open", "vcs_action")),
    OPS_SERVICES(
        "ops.services",
        IdeServer.OPS,
        "Services is the DevOps panel",
        setOf("services", "service_actions", "service_action", "service_open"),
    ),
    OPS_PROJECT(
        "ops.project",
        IdeServer.OPS,
        "The project model",
        setOf("project", "modules", "dependencies", "dependency_add", "plugins"),
    ),
    OPS_IDE("ops.ide", IdeServer.OPS, "Move the IDE for the user", setOf("tool_window", "settings_open", "ide_action", "notify")),
    OPS_ACTIONS("ops.actions", IdeServer.OPS, "Every menu entry is one action away", setOf("actions", "menu", "appearance", "ui")),
    OPS_DATA(
        "ops.data",
        IdeServer.OPS,
        "Data through the IDE",
        setOf("db_connections", "db_schema", "db_query", "http_files", "http_run", "http_open", "ssh_hosts"),
    ),
    COMMON_SHOW("common.show", null, "Asked to see something, open it in the IDE", emptySet()),
    COMMON_QUERY("common.query", null, "Questions about the project are answered from the IDE", emptySet()),
    COMMON_PRS("common.prs", null, "Pull requests: list, ask, open in the IDE, review", emptySet()),
    COMMON_BATCH("common.batch", null, "Independent calls go out together; lists go whole", emptySet()),
    COMMON_AGENTS("common.agents", null, "Agents and subagents inherit these rules", emptySet()),
    COMMON_TOOLS("common.tools", null, "Own tools live under .claudetools, ignored by git", emptySet()),
    COMMON_FALLBACK("common.fallback", null, "A failing server is said out loud, never worked around silently", emptySet()),
    COMMON_REPORT("common.report", null, "A native tool used while its IDE replacement existed is a defect", emptySet()),
    ;

    companion object {
        fun of(key: String): IdeRule? = entries.firstOrNull { it.key == key }

        fun forServer(server: IdeServer): List<IdeRule> = entries.filter { it.server == server }

        val common: List<IdeRule> get() = entries.filter { it.server == null }

        fun active(rules: Set<IdeRule>, servers: Set<IdeServer>): Set<IdeRule> =
            if (servers.isEmpty()) emptySet() else rules.filterTo(LinkedHashSet()) { it.server == null || it.server in servers }

        fun parse(csv: String): Set<IdeRule> =
            csv.split(',').map { it.trim() }.mapNotNull(::of).toSet()

        fun csv(rules: Collection<IdeRule>): String = rules.sortedBy { it.ordinal }.joinToString(",") { it.key }
    }
}
