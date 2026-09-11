# MCP roadmap — sprint board

A living document. `DIRECTIVES.md` says **what** is decided and **why**; this says **in what order** and
**what is done**. It is updated in the same turn the state changes, never at the end.

**Legend**: ☐ to do · ◐ in progress · ☑ done and committed.

**Sprint exit**: green on `Tool: tests kotlin`, `Tool: tests frontend` and `Tool: lint`, read from the
tail of `.claudetools/run/out/<name>.log`; clean diagnostics on every file touched; a signed commit.

**Detail rule**: only the current sprint and the next one are broken down into tasks. The rest are a
headline, expanded when they start, because what is learnt in one changes the next.

---

## Sprint 0 — the floor

First, because everything else rests on the compiler telling the truth.

- ☐ `allWarningsAsErrors = true` in `build.gradle.kts`, and fix whatever turns red.
- ☑ `sinceBuild` pinned to the current stable — already `253.29346.138`, `untilBuild` `263.*`.
- ☐ A contract test scanning sources for the `@ApiStatus.Internal` symbols the compiler cannot flag.
- ☐ `FileRollback.kt` off the `@TestOnly` two-argument `runWriteCommandAction`, onto the named builder.
  It is why reverting a file offers an undo entry called `Undefined`.
- ☐ `TerminalLauncher.kt` off the reflected five-argument `createNewSession`, which is
  `@ApiStatus.Internal`, onto `createShellWidget(workingDirectory, tabName, requestFocus,
  deferSessionStartUntilUiShown)`.
- ☐ Coroutines enabled for `model/mcp/` and `controller/mcp/` only.

**Commit**: `build: warnings are errors, and no internal platform API gets in`

## Sprint 1 — the third parties go

- ☐ `IdeServer` becomes `CODE`, `RUN`, `VCS`, `OPS`, `JETBRAINS`. Index and Debugger are gone.
- ☐ Delete `IdeRule.kt` and the `knownTools` field.
- ☐ `IdeMcpPrompt` shrinks to a paragraph; the injected block names no tool at all.
- ☐ `GodMode`, `IdeMcpState`, `SettingsIdeMcpSection`, `McpConfigBuilder`, `SessionLauncher`,
  `LaunchOptions` and `JcefSettingsMenu` move to the new model.
- ☐ `PluginInstaller` and `IdeServerControls` stay, serving the JetBrains server only.
- ☐ Tests and documents follow in the same commit.

**Commit**: `refactor(mcp): the IDE integration is servers of our own; the third-party servers are gone`

## Sprint 2 — TOON, and a round trip that works

The delicate one. The codec lands before any tool does.

- ☐ A TOON codec in Kotlin for the core and in dependency-free Java for the helper.
- ☐ Validated against the specification's own published fixtures, plus a round-trip test. All four
  forms: inline, list, tabular and keyed tabular, with nested field groups.
- ☐ `JsonRpc`, a dual-era `McpServer`, `ToolSpec`, `ToolArgs`, `ToolResult`, `OutputBudget`.
- ☐ `StdioBridge`: translates JSON-RPC to TOON and pumps against the socket.
- ☐ Four Unix sockets in a 0700 directory, removed on dispose.
- ☐ The three meta-tools, with the test that `tools/list` returns exactly three entries.
- ☐ A queue per server: immediate acknowledgement, out-of-order replies, timeout, cancellation, and a
  bounded depth that refuses with an actionable message when full.
- ☐ The auth token: generated, handed over at `init`, in memory, rotated every 30 minutes with an
  overlap. Tests that a missing or expired token is refused and that the secret never reaches a log.
- ☐ The guard at the `run(tool, args)` dispatcher, with a test for the nested argument. Nothing under
  `permission/` is touched.
- ☐ The `read` and `search` domains of the `code` server, four tools each at most.

**Commit**: `feat(mcp): four MCP servers inside the plugin, each asked for its tools on demand`

## Later sprints — headlines

| # | Headline | Note |
|---|---|---|
| 3 | Navigation, file outline, the IDE's problems and its inspections | The split into four-tool domains is decided on entry |
| 4 | Edits, refactors, quick fixes and formatting, each opening the review diff | Carries "one Claude edit, one undo entry" |
| 5 | Builds, tests, run configurations and a shell in the IDE's terminal | |
| 6 | The debugger | Nine tools as drafted: group by argument, then split the domain |
| 7 | Git, and the forge through the IDE's own views | |
| 8 | The Services panel, and every action the IDE offers on its nodes | The DevOps piece |
| 9 | The IDE's databases and HTTP client; SSH read-only | The fragile one: reflection isolated in a gateway |
| 10 | Claude configures the IDE it works in, and speaks up inside it | |
| 11 | Any MCP client can drive the IDE | |
| 12 | The first-run tutorial and the version text | Closes the release |

Two further horizons are already researched and become sprints once the release is out: **the code as a
program** (live templates, injected languages, bookmarks, the workspace model, PSI as a tree, the
indexes, UAST), and **presence in the IDE** (gutter and inlay markup, the editor banner, arbitrary
diffs, scratch files, the status bar, and surviving a split IDE).

## Open decisions

- The exact split of domains under the four-tool ceiling, sprint by sprint.
- Whether the SQL argument is renamed so it inherits the guard's existing verdict.
- Argon2id instead of PBKDF2 for the plugin passphrase: needs a new dependency, decided separately.
- Whether a completion tool is viable at all, which depends on a stable public way to build its
  parameters.
