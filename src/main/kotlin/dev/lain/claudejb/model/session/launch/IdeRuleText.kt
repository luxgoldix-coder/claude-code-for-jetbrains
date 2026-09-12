package dev.lain.claudejb.model.session.launch

internal object IdeRuleText {

    val RULES: Map<IdeRule, String> = mapOf(
        IdeRule.CODE_READ to "To look at a file call read_file, several at once with paths: it reads through the IDE, unsaved " +
            "edits included, and shows the file in the editor's preview tab without taking the focus. On a big file call " +
            "file_outline first and read only the ranges you need. To list a directory call list_directory. Never ls, " +
            "cat, head, tail or sed to look at anything.",
        IdeRule.CODE_SEARCH to "To find text call search_text with queries, one call for all of them, regex when needed; to " +
            "find files by name or glob call find_files; to find a symbol by name call find_symbols; never grep, rg or " +
            "find. The IDE's index answers in one round trip and sees a resolved program, not characters.",
        IdeRule.CODE_NAVIGATE to "To go from a symbol to where it is declared call definition; to its callers and readers, " +
            "references; to what implements or overrides it, implementations; for its type, signature and documentation, " +
            "symbol_info; for its class or call tree, hierarchy. All resolve through the IDE's index; pass positions to " +
            "resolve several symbols in one call.",
        IdeRule.CODE_EDIT to "To change a file call replace_text with edits, one call for every file you touch, old and new " +
            "text exact; to add lines call insert_text; to create a file call create_file; to rewrite whole files call " +
            "write_file with files. Every edit opens as a diff the user sees and reviews, then lands in a real editor tab. Never " +
            "Edit, Write, sed, awk, tee or a heredoc.",
        IdeRule.CODE_EDIT_OPS to "To take back the last change of a file call undo, to put it back call redo: both go through the " +
            "IDE's undo stack for that file's editor, as the Edit menu would. To replace text or a regular expression " +
            "across files call search_replace, with paths to limit it, one undoable command per file and the first " +
            "changed file shown in the editor. For join, duplicate, delete, indent or unindent at a line call line_ops.",
        IdeRule.CODE_REFACTOR to "To rename a symbol call rename: the IDE's refactoring updates every usage. To move a file " +
            "call move_file, to delete one safely call safe_delete: both fix the imports and refuse while usages remain. " +
            "Never rename or move by editing text.",
        IdeRule.CODE_FORMAT to "After editing call reformat on the touched files and optimize_imports on them, both with paths " +
            "in one call: the IDE's formatter with the project's code style, not yours.",
        IdeRule.CODE_DIAGNOSTICS to "Before calling work done call problems on every file you touched and project_problems " +
            "for the whole project; a warning you introduced is yours to fix. Call problems_view to show a tab of the " +
            "Problems window (Qodana, Vulnerable Dependencies and Security Analysis included); call inspections to see " +
            "the profile's inspections and inspect to run them on a file on request; every reveal is without focus.",
        IdeRule.CODE_EDITOR to "Call open_file to put a file in front of the user at a line, in a tab, without taking the " +
            "focus. Call active_file to learn where the user is: file, caret and selection. On an indexing error call " +
            "index_status with wait and retry once it is ready. For the Code menu at a position (override, implement, " +
            "generate, surround, unwrap, comment, move statement or line, rearrange, fold, live templates, quick " +
            "documentation) call editor_action with path, line and column: the editor performs it with the caret there.",
        IdeRule.CODE_RECENT to "To know where the user has been call recent: the files they opened last (kind=files) or " +
            "changed last (kind=changed_files), from the IDE's editor history. To move their editor through that " +
            "history when asked call navigate_history with back, forward, last_change or next_change. To show the " +
            "clipboard against a file call compare_clipboard: the IDE's diff window opens without focus. To list or " +
            "switch the theme, the color scheme, the keymap or the code style call scheme.",
        IdeRule.RUN_BUILD to "To build call build: the IDE's own build with the compiler's errors and their positions, shown " +
            "in the Build window. To run tests call run_tests with a path, a name or a class and read the tree it " +
            "returns; tests lists what the project has. Never a shell, a script or Gradle by hand to build or test.",
        IdeRule.RUN_RUN to "Call run_configurations to see what the project already runs and run_configuration to run one, " +
            "exactly as the Run button does, output streaming to the chat and to the Run window; a utility that has no " +
            "configuration gets one under .idea/runConfigurations named Tool: <name>. Call processes to list or stop " +
            "what is running.",
        IdeRule.RUN_TERMINAL to "A command goes through shell, never Bash: it runs over the socket in the Terminal window " +
            "the user sees, in a tab named Claude, with its exit code and the end of its output, while a new process " +
            "would cost a guard pass and a permission. Several commands go in one call, chained with ; or &&. The tab is " +
            "shown without focus and never switched while the user is in the Terminal.",
        IdeRule.RUN_DEBUG to "To debug call session to start a run configuration under the debugger, breakpoint to set or " +
            "clear breakpoints, step to step over, into or out, frames for the stack and values for the variables of a " +
            "frame: the IDE shows the execution point as you go. Never prints. Call session with stop when done.",
        IdeRule.VCS_READ to "Before deciding anything about the tree call git_status, git_diff, git_log and git_branches: " +
            "git as the IDE sees it, read-only. git_log with hashes returns each commit and selects it in the Log.",
        IdeRule.VCS_WRITE to "To stage call git_stage with paths, to commit call git_commit with paths and a message, both " +
            "in one call for the whole list; to create, check out or delete a branch call git_branch; to fetch, pull or " +
            "push call git_remote. Each goes through the IDE's Git, signs as the IDE would and draws one card; the " +
            "commit message is read by the guard like any other text.",
        IdeRule.VCS_FORGE to "Call vcs_open to show the Log (at a hash, or only a range with range=A..B), a file's history, " +
            "the Commit window or the Pull Requests view, without taking the focus; call vcs_action to open any entry of " +
            "the IDE's Git menu and its GitHub and GitLab submenus by name (pull, push, merge, rebase and their abort or " +
            "continue, branches, stash, shelve, tag, reset, worktrees, annotate, clone, pull and merge requests, gists, " +
            "accounts) for the user to finish, with path or hash when the entry acts on a file or a commit. Never gh or " +
            "glab.",
        IdeRule.VCS_LOG_OPS to "For what the Log's commit menu offers on a commit (cherry-pick, checkout, browse at revision, " +
            "compare with local, reset, revert, undo, reword, fixup, squash, drop, interactive rebase, push up to, new " +
            "branch or tag, copy revision, open in browser) call commit_action with the hash: the commit is selected in " +
            "the Log and the action runs as the menu would. For what the Branches popup offers (merge, rebase, compare, " +
            "diff with local, rename, delete, checkout, checkout as new, new tag) call branch_op: the IDE's own branch " +
            "machinery with its progress and conflict handling. Call worktrees and remotes to list, add or remove " +
            "working trees and remotes through the IDE's Git.",
        IdeRule.VCS_CHANGES to "For the uncommitted work call stash (save, pop, apply, drop, list through the IDE's Git), " +
            "shelve (the IDE's shelf: list, shelve by name, unshelve), patch (create writes the changes as a unified diff, " +
            "apply opens the IDE's Apply Patch dialog) and rollback (the IDE's Rollback on the given files, undoable from " +
            "Local History); paths go all in one call.",
        IdeRule.VCS_HISTORY to "To know who wrote a line call blame: the IDE's annotations, and the gutter is shown. For the " +
            "commits that touched a file call file_history, and the history tab is shown. For the IDE's Local History " +
            "call local_history: show its view, put a label before a risky change, revert to a label. For a file's content " +
            "at a branch, tag or commit call file_at: the IDE's diff against the working tree is shown.",
        IdeRule.OPS_SERVICES to "The Services window is the DevOps panel: call services to see its tree as the user does, " +
            "service_actions to see what the IDE offers on a node, service_action to perform one exactly as clicking it " +
            "would, service_open to reveal the node. Never kubectl, docker or podman.",
        IdeRule.OPS_PROJECT to "Call project for the SDK and the structure, modules for the modules with their roots, " +
            "dependencies for what a module depends on, all from the IDE's project model; dependency_add adds a library " +
            "through Gradle, Maven or npm as the IDE would; call plugins before relying on a tool window, an action or a " +
            "file type a plugin provides.",
        IdeRule.OPS_IDE to "Call tool_window to open or close a tool window without taking the focus, settings_open to open " +
            "Settings at a page, ide_action to perform any registered action by id when no other tool covers it, and " +
            "notify to raise a notification the user sees.",
        IdeRule.OPS_ACTIONS to "Every entry of the IDE's menus is an action: call actions with a fragment of its text or id to " +
            "find it, plugins included, and menu with a path such as Code/Analyze or Git/GitHub to walk the main menu " +
            "as the user sees it; then fire it with ide_action and a target. Call appearance to flip presentation, " +
            "distraction-free, full-screen, zen, compact or the Presentation Assistant, and ui to show or hide the " +
            "toolbar, navigation bar, tool window bars, status bar or main menu; both say the state they left.",
        IdeRule.OPS_WINDOW to "Call tabs to see the editor's tab groups or to close, pin, split or move a tab as the Window " +
            "menu would; layout to store, restore or hide the tool window layout; zoom to zoom the editor's font or the " +
            "whole IDE; editor_settings to show or hide line numbers, whitespace, soft wraps or gutter icons in every " +
            "editor, saying the state it left.",
        IdeRule.OPS_DATA to "Call db_connections, db_schema and db_query for the Database window's data sources; http_files " +
            "to find the HTTP Client's request files, http_run to run one with its response console and http_open to show " +
            "one in the editor; ssh_hosts for the configured SSH hosts. Never psql, curl or ssh from the command line.",
        IdeRule.COMMON_SHOW to "Asked to see or open something: open it in the IDE with the tool that reveals it and say " +
            "what you opened; the user's focus stays where it was.",
        IdeRule.COMMON_QUERY to "Questions about the project or the IDE are answered from its tools, never from memory; " +
            "say what you looked at.",
        IdeRule.COMMON_PRS to "Pull requests: open the IDE's own view with vcs_open(view=pull_requests), ask which one, " +
            "review it there, report.",
        IdeRule.COMMON_BATCH to "One call carries the whole list: read_file(paths), write_file(files), replace_text(edits), " +
            "search_text(queries), git_commit(paths). Never one item per call; independent calls go out in one message.",
        IdeRule.COMMON_AGENTS to "Every agent you spawn receives this block verbatim and works the same way, in batches.",
        IdeRule.COMMON_TOOLS to "Scripts serve utilities, never builds or tests; they go under ./.claudetools, /.claudetools/ in " +
            ".gitignore first.",
        IdeRule.COMMON_FALLBACK to "Name a failing server in one line before any fallback, never silently.",
        IdeRule.COMMON_REPORT to "A native tool used while ours existed is the defect: name it and stop.",
    )
}
