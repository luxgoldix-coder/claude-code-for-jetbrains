# Skill inventory — what Claude can do in the IDE

Every capability Claude has inside the IDE is an MCP tool served by the plugin itself. This is the
inventory: what exists today, how each tool is reached, and what the roadmap still owes. The order of
work lives in [`MCP_ROADMAP.md`](MCP_ROADMAP.md); the architecture in [`../DIRECTIVES.md`](../DIRECTIVES.md).

## How a tool is reached

Four MCP servers run inside the plugin, one per family, over Unix sockets: **`code`**, **`run`**,
**`vcs`** and **`ops`**. Each one exposes only three meta-tools, so nothing loads up front:

| Meta-tool | What it does |
|---|---|
| `domains()` | Lists the server's domains, one line each. Always first. |
| `tools(domain)` | Lists the tools of one domain with their parameters. Only for the domain about to be used. |
| `run(tool, args)` | Runs one tool. Every result is TOON. The Security Guard judges `args` before anything runs. |

A request in the chat reaches a tool through that ladder: *"read `Foo.kt`"* becomes
`mcp__code__domains` → `mcp__code__tools(read)` → `mcp__code__run(read_file, {path})`. The examples below
write only the last step, as `server ▸ tool {args}`.

Rules that hold for every tool:

- **Lists**: any tool with a `paths`, `queries`, `names`, `positions`, `edits`, `files`, `hashes` or
  `statements` parameter runs once per item in a single call and draws one card per item. Up to 50 items.
  `git_stage` and `git_commit` take `paths` as one call, not a batch.
- **Long tools** (`build`, `run_configuration`, `run_tests`, `shell`, `http_run`) stream their output to the
  card, answer `status: running` after `wait` seconds, and are resumed with `job`.
- **Positions** are 1-based `line` and `column`; paths are absolute or relative to the project root.
- Every enumerating tool carries a `max`, and says `truncated` when it hit it.
- A tool marked *mutates* is a change the user sees in the IDE (a diff, a refresh, a dialog).

## `code` — the project as the IDE resolves it

### read · search

| Tool | Capability | When · example |
|---|---|---|
| `read_file` | A file as the editor holds it, unsaved edits included; `offset`/`limit` for big files; several with `paths`. | Any "look at", "open", "what does X contain". `code ▸ read_file {paths: ["src/A.kt", "src/B.kt"], limit: 120}` |
| `search_text` | Text or regex across the project; one row per hit with file and line, no text. | "where is X used", "find the string". `code ▸ search_text {queries: ["TODO", "class .*Test"], regex: true}` |
| `find_files` | Files by exact name or glob. | "where is the file called". `code ▸ find_files {names: ["*.http", "Guard*.kt"]}` |
| `list_directory` | A directory as the project tree shows it, excluded entries left out, `depth` levels. | "what is in this folder". `code ▸ list_directory {path: "src/main/kotlin", depth: 2}` |

### navigate · outline · hierarchy

| Tool | Capability | When · example |
|---|---|---|
| `find_symbols` | Classes, functions and other named symbols whose name contains the query, as Go to Symbol does; `libraries` to include them. | "which classes are called …Tools". `code ▸ find_symbols {queries: ["Tools", "Guard"]}` |
| `definition` | The declaration the reference at a position resolves to. | "where is this defined". `code ▸ definition {path: "A.kt", line: 9, column: 24}` |
| `references` | Every place that references the symbol at a position. | "who calls this", "is this used". `code ▸ references {path: "A.kt", line: 14, column: 9}` |
| `implementations` | Implementations or overrides of the symbol at a position. | "who implements this interface". `code ▸ implementations {path: "I.kt", line: 6}` |
| `file_outline` | The declarations of a file as a tree with their lines, like the Structure view. | Before reading a big file. `code ▸ file_outline {paths: ["Session.kt"], depth: 2}` |
| `symbol_info` | Kind, name, declaring signature and location of the symbol at a position. | "what is this thing". `code ▸ symbol_info {positions: [{path: "A.kt", line: 21, column: 47}]}` |
| `hierarchy` | Callers or callees of the symbol at a position, nested up to `depth` 3. | "trace who reaches this". `code ▸ hierarchy {path: "A.kt", line: 123, kind: "callers", depth: 3}` |

### diagnostics · inspect

| Tool | Capability | When · example |
|---|---|---|
| `problems` | Errors and warnings the IDE's analysis shows for a file (opens it), with line, column, severity and inspection; `severity` error/warning/weak/all. | Before calling any edit done. `code ▸ problems {paths: ["A.kt", "B.kt"], severity: "warning"}` |
| `project_problems` | Everything the Problems view lists across the project; `group` filters by inspection family or plugin. | "is the project clean", "what does Qodana say". `code ▸ project_problems {group: "Qodana"}` |
| `problems_view` | Lists the Problems tool window's tabs, or shows one to the user. | "show me the security findings". `code ▸ problems_view {tab: "Security Analysis"}` |
| `inspections` | The inspections of the current profile — id, name, group, enabled — filtered by a query. | To find an inspection id. `code ▸ inspections {query: "unused"}` |
| `inspect` | Runs the profile's enabled inspections on a file, or one by id; findings with line, severity and message; hints below `severity` stay out. | "run the inspections on this file", "is there anything unused here". `code ▸ inspect {path: "A.kt", inspection: "UnusedSymbol"}` |

### edit · refactor · format

| Tool | Capability | When · example |
|---|---|---|
| `replace_text` *mutates* | One literal replacement (all with `replace_all`), one undo entry, saved, shown as a diff; several files with `edits`. | Any targeted change. `code ▸ replace_text {edits: [{path: "A.kt", old_string: "x", new_string: "y"}]}` |
| `insert_text` *mutates* | Whole lines before a line (one past the end appends). | Adding a member or an import. `code ▸ insert_text {path: "A.kt", line: 5, content: "fun twice() = 2"}` |
| `create_file` *mutates* | A new file, directories created, opened; fails if it exists. | "create a test for". `code ▸ create_file {files: [{path: "src/test/X.kt", content: "…"}]}` |
| `write_file` *mutates* | A whole rewrite as one undo entry and one diff, or creation when absent. | A file that changes more than it keeps. `code ▸ write_file {path: "A.kt", content: "…"}` |
| `rename` *mutates* | The IDE's Rename on the symbol at a position, or the file; every reference follows; fails on conflict. | "rename X to Y". `code ▸ rename {path: "A.kt", line: 6, column: 9, new_name: "salute"}` |
| `move_file` *mutates* | The IDE's Move: packages, imports and references follow. | "move this into package p". `code ▸ move_file {path: "A.kt", destination: "src/main/kotlin/p"}` |
| `safe_delete` *mutates* | Deletes a symbol or a file only when nothing uses it; otherwise lists the blocking usages. | "remove this if unused". `code ▸ safe_delete {path: "A.kt", line: 7, column: 9}` |
| `reformat` *mutates* | Reformat Code on a file or a line range, with the project's code style. | After editing. `code ▸ reformat {paths: ["A.kt", "B.kt"]}` |
| `optimize_imports` *mutates* | Optimize Imports on a file. | After editing. `code ▸ optimize_imports {path: "A.kt"}` |

### editor

| Tool | Capability | When · example |
|---|---|---|
| `open_file` | Opens a file at a line and column, as Go to File does. | "show me", and on every file edited. `code ▸ open_file {path: "A.kt", line: 14}` |
| `active_file` | The selected editor with caret and selection, plus every open file. | "what am I looking at". `code ▸ active_file {}` |
| `index_status` | Whether the IDE is indexing; `wait` blocks until it is done. | On an indexing error, before symbol tools. `code ▸ index_status {wait: true}` |

## `run` — build, run, test, shell, debug

| Tool | Capability | When · example |
|---|---|---|
| `build` *mutates* | The IDE's build, incremental or `rebuild`, with the compiler's errors and positions; streamed. | "does it compile". `run ▸ build {kind: "build", wait: 110}` |
| `run_configurations` | The run configurations as the Run combo shows them: name, type, temporary, selected. | Before running anything. `run ▸ run_configurations {}` |
| `run_configuration` *mutates* | Starts one as the Run button does, before-launch tasks included; exit code and console tail; several with `names`. | "run the gates", any project script that already has a configuration. `run ▸ run_configuration {name: "Tool: lint", wait: 110}` |
| `processes` *mutates* | The Run tool window's tabs, or stops one by name. | "is it still running", "stop it". `run ▸ processes {action: "stop", name: "Kotlin tests"}` |
| `run_tests` *mutates* | Tests through the IDE's runner: a file, several, the test at a line, or a named configuration; pass/fail/ignored and each failure's message and frame. | "run this test". `run ▸ run_tests {name: "ToolModelTest", wait: 110}` |
| `tests` | The test classes and methods the IDE's frameworks recognise in a file, with lines. | "what tests are in here". `run ▸ tests {path: "src/test/X.kt"}` |
| `shell` *mutates* | A command in the user's shell inside a Terminal tab; exit code and tail. Replaces Bash. | Any command; several chained in one call. `run ▸ shell {command: "git log -3 --oneline", wait: 20}` |
| `session` *mutates* | Start a configuration under the debugger and wait for the first stop; status with frames and variables; stop; list. | "debug this test". `run ▸ session {action: "start", name: "ToolModelTest"}` |
| `step` *mutates* | over, into, out, resume, pause, run_to a line, or wait; answers with the session status. | Once suspended. `run ▸ step {kind: "run_to", path: "A.kt", line: 22}` |
| `frames` | Threads and the stack of one; `frame` selects the current frame for `values`. | "where is it stopped". `run ▸ frames {max: 5}` |
| `values` *mutates* | Variables of the current frame; `eval` an expression; `set` a variable. | "what is x here". `run ▸ values {action: "eval", code: "tools.size"}` |
| `breakpoint` *mutates* | Add (with `condition`, `temporary`), remove or list line breakpoints. | Before `session`. `run ▸ breakpoint {action: "add", path: "A.kt", line: 21}` |

## `vcs` — Git and the forge through the IDE

| Tool | Capability | When · example |
|---|---|---|
| `git_status` | The working tree as the Changes view sees it: branch, HEAD, upstream, ahead/behind, every changed path with its type. | Before staging or committing, and before any claim about the tree. `vcs ▸ git_status {}` |
| `git_log` | Recent commits with hash, subject, author, date and files; one or several `hashes` with their paths; `all_branches`. | "what changed lately", "what did commit X touch". `vcs ▸ git_log {hashes: ["5db1226"]}` |
| `git_diff` | The unified diff of the uncommitted changes: whole tree, one path, or several. | Reviewing before a commit. `vcs ▸ git_diff {paths: ["A.kt"], max_lines: 200}` |
| `git_branches` | Every local and remote branch with its commit, current first. | "which branches exist". `vcs ▸ git_branches {}` |
| `git_stage` *mutates* | Stages (`add`) or unstages (`reset`) paths through the IDE's Git. | Only the paths touched, never blind. `vcs ▸ git_stage {action: "add", paths: ["A.kt"]}` |
| `git_commit` *mutates* | Commits what is staged, or only `paths`; signing and hooks as the user's Git configures them. | One commit per logical unit. `vcs ▸ git_commit {message: "fix(x): …", paths: ["A.kt"]}` |
| `git_branch` *mutates* | Creates a branch or checks one out (`start_point` creates it there). Deleting is the user's. | "start a branch for". `vcs ▸ git_branch {action: "checkout", name: "feature/x", start_point: "develop"}` |
| `git_remote` *mutates* | Fetch, pull or push with the IDE's credentials; returns upstream and ahead/behind. Push is the maintainer's call. | "fetch". `vcs ▸ git_remote {action: "fetch"}` |
| `vcs_open` | Shows a VCS view: the Git log (at a `hash`, or only a `range` such as `v5.8.1..HEAD`), a file's history, the Commit window, or the pull-requests view. | "open the log", "compare the branch with the last release". `vcs ▸ vcs_open {view: "log", range: "v5.8.1..HEAD"}` |
| `vcs_action` *mutates* | Opens one of the IDE's Git, GitHub or GitLab dialogs (pull, push, merge, rebase, branches, stash, tag, reset, create_pull_request, …) for the user to finish. | When the user must confirm in the IDE. `vcs ▸ vcs_action {action: "branches"}` |

## `ops` — the Services panel, the project, the IDE, data

| Tool | Capability | When · example |
|---|---|---|
| `services` | The Services tree as the user sees it: path, name, contributing plugin, state; `filter`. Only nodes with services, as the view shows. | Before any other services tool. `ops ▸ services {filter: "Docker"}` |
| `service_actions` | The actions the IDE offers on a node, with id, text and enabled. | To know what `service_action` can do. `ops ▸ service_actions {path: "Docker/Docker/Containers/web"}` |
| `service_action` *mutates* | Performs one of those actions exactly as clicking it would. | "stop the container", "connect to Docker". `ops ▸ service_action {path: "Docker/Docker/Containers/web", action: "Stop Container"}` |
| `service_open` | Reveals a node in the Services window. | "show me the cluster". `ops ▸ service_open {path: "Docker/Docker"}` |
| `project` | Name, base path, SDK, indexing, module count, active VCSs. | First call in an unknown project. `ops ▸ project {}` |
| `modules` | The modules as Project Structure shows them. | "how is the project split". `ops ▸ modules {}` |
| `dependencies` | One module's order entries in classpath order, with scope. | "what does the main module depend on". `ops ▸ dependencies {module: "app.main"}` |
| `dependency_add` *mutates* | Adds an existing library to a module through the project model (not for Gradle/Maven, which edit the build file). | Plain IntelliJ projects only. `ops ▸ dependency_add {module: "app", library: "junit", scope: "test"}` |
| `ide_action` *mutates* | Any registered IDE action by id, as its menu entry would. | What no other tool covers. `ops ▸ ide_action {action_id: "Git.CompareWithBranch"}` |
| `tool_window` *mutates* | Open, close or list tool windows. | "show the Problems view". `ops ▸ tool_window {action: "open", id: "Problems View"}` |
| `settings_open` *mutates* | Settings at a page by display name. | "open the plugin settings". `ops ▸ settings_open {name: "Claude Code"}` |
| `plugins` | The IDE's plugins with id, version and enabled; `filter`. | Before relying on a plugin; to know the IDE build (`com.intellij`). `ops ▸ plugins {filter: "database"}` |
| `notify` *mutates* | A balloon in the IDE's notification area. | A finished long task or a decision needed while the chat is hidden. `ops ▸ notify {title: "Build", message: "green", kind: "info"}` |
| `db_connections` | The Database tool window's data sources: name, DBMS, redacted URL. | First db call. `ops ▸ db_connections {}` |
| `db_schema` | Tables and views of a source, or the columns of one table. | "what tables are there". `ops ▸ db_schema {connection: "local", table: "users"}` |
| `db_query` *mutates* | One or several SQL statements over the IDE's connection and credentials; rows or update count. Reads by intent; a write is the user's to approve. | "how many rows". `ops ▸ db_query {connection: "local", code: "select count(*) from users"}` |
| `http_files` | The project's `.http`/`.rest` request files. | Before `http_run`. `ops ▸ http_files {}` |
| `http_run` *mutates* | Runs every request of a file through the HTTP Client's run configuration; console tail; streamed. | "call the API from the .http file". `ops ▸ http_run {path: "api/users.http", wait: 45}` |
| `http_open` | Opens a request file in the editor. | "show me the requests". `ops ▸ http_open {path: "api/users.http"}` |
| `ssh_hosts` | The SSH hosts the IDE knows: host, port, user, authentication kind; never the secret. | "which hosts are configured". `ops ▸ ssh_hosts {}` |

## Not tools, but capabilities that ride on them

- **The Security Guard** judges every `run(tool, args)` inside the server; a refusal comes back as the
  tool's error with the rule and the string that tripped it. The answer is to change that string, not the tool.
- **Cards**: every own call is a card in the chat, one per item in a list, with live lines for long tools, a
  diff and Restore for edits, and a one-click link into the IDE (commit, log, tool window, terminal, run,
  build, problems, diff, action).
- **The rules block**: the `<ide-integration>` fragment rides the system prompt and a hook every turn and
  names which tool replaces which native one; each rule is a switch in Settings ▸ Claude Code.

## Board — phase 1, "Claude on JetBrains"

The order of this phase. **Legend**: ☐ to do · ◐ in progress · ☑ done and committed. A row moves in the
same commit that lands its tools, and the tools enter the tables above in that commit. Every domain holds
at most four tools; a capability that needs more is a new domain. Two laws close the surface: every
registered action is reachable through `actions` + `ide_action(target)`, and every action Claude takes is
mirrored in the IDE without taking the user's focus.

### P0 — foundations and the reported bugs

| Capability | Where | Status |
|---|---|---|
| An own call made by a subagent gets its card, nested under the agent | `ToolEvents` | ☐ |
| Permission popup, approval rows and guard log name the tool (`code ▸ read_file ▸ path`), not `run` | `OwnTools.display` on every surface | ☑ |
| An agent still reasoning is never shown as completed | `AgentEnding` | ☑ |
| The rules block has no length limit; the test asserts coverage and prints the size | `IdeMcpPrompt`, `IdeRuleText`, `IdeRule`, `IdeMcpPromptTest`, `IdeRuleCoverageContractTest` | ☑ |
| 250 lines per file, imports not counted | `FileSizeContractTest` | ☑ |
| Reveal without focus on every existing tool; `FocusKeeper` returns the focus the platform steals; the terminal is never focused nor its tab switched | `FocusKeeper`, every domain, `FocusContractTest` | ☑ |
| Live mirror with one switch (Settings ▸ Claude Code, ON): reads in the preview tab, edits in a real tab, commits in the log, nodes in Services, problems in their tab, runs in their window | `Reveal`, `IdeMcpState.mirror`, Settings section | ☑ (build, run, tests and debug are shown by the platform itself, focus-free by default) |
| `vcs_open(log, range)` off the deprecated `openLogTab` | `GitLogNavigator` | ☑ |
| `run_tests(path)` prefers the framework producer over Gradle | `TestTools` | ☐ |

### P1 — every action, with its target

| Capability | Tools (domain) | Status |
|---|---|---|
| The action catalogue of the user's IDE, enabled-in-context; the main menu tree | `actions`, `menu` (`actions`) | ☐ |
| Appearance and UI toggles | `appearance`, `ui` (`actions`) | ☐ |
| `ide_action` with a target: file/position, commit, Services node | `ide_action` +`path`/`line`/`column`/`hash`/`node` (`ide`) | ☐ |
| Code menu editing actions at a position | `editor_action` (`editor`) | ☐ |
| Undo, redo, replace in path, line operations | `undo`, `redo`, `search_replace`, `line_ops` (`edit_ops`) | ☐ |
| Recent files/locations/changes, back/forward, clipboard compare, schemes | `recent`, `navigate_history`, `compare_clipboard`, `scheme` (`recent`) | ☐ |
| Editor tabs, layouts, zoom, editor settings | `tabs`, `layout`, `zoom`, `editor_settings` (`window`) | ☐ |
| Every Git-menu dialog by name | `vcs_action` table extended (`forge`) | ☐ |

### P2 — Git as the log and the branches panel do it

| Capability | Tools (domain) | Status |
|---|---|---|
| The commit context menu on a hash; branch operations through `GitBrancher`; worktrees; remotes | `commit_action`, `branch_op`, `worktrees`, `remotes` (`log_ops`) | ☐ |
| Stash, shelve, patches, rollback | `stash`, `shelve`, `patch`, `rollback` (`changes`) | ☐ |
| Blame, file history, local history, a file at a ref | `blame`, `file_history`, `local_history`, `file_at` (`history`) | ☐ |

### P3 — pull and merge requests as data

| Capability | Tools (domain) | Status |
|---|---|---|
| GitHub pull requests listed and opened through the IDE's GitHub plugin | `pull_requests`, `pull_request` (`forge`), `GitHubGateway` | ☐ |
| GitLab merge requests: actions and view; data if a public path exists | `vcs_action`, `GitLabGateway` | ☐ |

### P4 — analysis, views, files, refactorings

| Capability | Tools (domain) | Status |
|---|---|---|
| Inspect a scope, code cleanup, dependency analysis, data flow | `inspect_scope`, `cleanup`, `dependencies`, `dataflow` (`analyze`) | ☐ |
| Stack traces, duplicates, nullity, related symbols | `stack_trace`, `duplicates`, `infer_nullity`, `related` (`analysis`) | ☐ |
| Diffs, compare, mark directory as, open in | `diff_show`, `compare`, `mark_as`, `open_in` (`views`) | ☐ |
| Copy path, file type, ignore files, delete | `copy_path`, `file_type`, `ignore`, `delete_file` (`files`) | ☐ |
| The Refactor menu beyond rename/move/safe-delete | `introduce`, `extract`, `inline`, `members` (`refactor_ops`) | ☐ |

### P5 — roadmap horizon 2

| Capability | Tools (domain) | Status |
|---|---|---|
| Live and file templates (R1) | `templates`, `template_apply`, `file_templates`, `file_from_template` (`templates`) | ☐ |
| Injected languages, quick documentation (R2; `docs` reveals the popup, `DocumentationTarget` is override-only) | `injections`, `inject_at`, `docs` (`language`) | ☐ |
| Bookmarks and the project view (R3; Structure follows the caret) | `bookmarks`, `bookmark_add`, `bookmark_remove`, `project_view` (`bookmarks`) | ☐ |
| The workspace model, read-only (R4) | `workspace` (`workspace`) | ☐ |
| PSI as a tree (R5) | `psi_tree`, `psi_at`, `psi_replace`, `psi_insert` (`psi`) | ☐ |
| The IDE's indexes (R6) | `index_keys`, `index_query`, `stub_query` (`index`) | ☐ |
| UAST (R7) | `uast_tree`, `uast_at` (`uast`) | ☐ |

### P6 — roadmap horizon 3, presence

| Capability | Tools (domain) | Status |
|---|---|---|
| Editor markup: highlights, gutter icons, inline hints (S1) | `mark_add`, `mark_remove`, `marks`, `hint_add` (`markup`) | ☐ |
| Banners, status bar, scratches (S2, S3) | `banner_show`, `banner_clear`, `status`, `scratch_create` (`presence`) | ☐ |
| One edit, one undo entry, one history label (S4) | `edit` domain | ☐ |

### P7 — Services in depth, the closed plugins

| Capability | Tools (domain) | Status |
|---|---|---|
| A node's data, extract, expand, events | `service_data`, `service_extract`, `service_expand`, `service_events` (`service_view`), `ServiceContentGateway` | ☐ |
| Deployment, SSH sessions, Qodana, vulnerable dependencies (S5) | `deployment`, `ssh_session`, `qodana`, `vulnerable_dependencies` (`remote`) | ☐ |

### P8 — run, tools, consoles, the client

| Capability | Tools (domain) | Status |
|---|---|---|
| Build a module or a file; run with coverage; more step kinds; watches | `build` +`kind`, `run_configuration` +`executor`, `step` +kinds, `watch` (`run`, `debug`) | ☐ |
| Edit configurations, attach, profile, coverage | `edit_configuration`, `attach`, `profile`, `coverage` (`run_ops`) | ☐ |
| Terminal tabs | `terminal_tabs` (`terminal`) | ☐ |
| Javadoc, launchers, XML, Markdown | `javadoc`, `launcher`, `xml`, `markdown` (`tools_menu`) | ☐ |
| Groovy console, Kotlin bytecode and configuration, Python console | `groovy_console`, `kotlin_bytecode`, `kotlin_configure`, `python_console` (`consoles`) | ☐ |
| Any MCP client drives the IDE (Q10); split mode (S6) | a minimal client in the repo; runtime detection | ☐ |

**Out, on record**: `completion` (its parameters have no public constructor), LSP (commercial IDEs; runtime
detection if ever needed), `sdk_set` (a global change that goes through the dialog), `structure_select`
(no public accessor; the Structure window follows the caret), and the third-party server tools discarded in
the roadmap's coverage matrix — change signature as data, super methods, structural search and replace,
module and project lifecycle, plugin install, IDE restart.
