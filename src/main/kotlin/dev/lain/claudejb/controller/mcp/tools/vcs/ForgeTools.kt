package dev.lain.claudejb.controller.mcp.tools.vcs

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import dev.lain.claudejb.controller.git.ForgeViewNavigator
import dev.lain.claudejb.controller.git.GitLogNavigator
import dev.lain.claudejb.controller.mcp.IdeActions
import dev.lain.claudejb.model.mcp.Param
import dev.lain.claudejb.model.mcp.Tool
import dev.lain.claudejb.model.mcp.ToolArgs
import dev.lain.claudejb.model.mcp.ToolDomain
import dev.lain.claudejb.model.mcp.ToolException
import dev.lain.claudejb.model.mcp.ToolResult
import dev.lain.claudejb.model.mcp.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class ForgeTools(private val project: Project, private val actions: IdeActions) {

    fun domain(): ToolDomain = ToolDomain(
        "forge",
        "The IDE's own VCS surface: its log, history, commit and pull-request views, and its Git, GitHub and GitLab dialogs",
        listOf(Tool(VCS_OPEN, ::open), Tool(VCS_ACTION, ::action)),
    )

    private suspend fun open(args: ToolArgs): ToolResult {
        val view = args.string("view")
        val hash = args.optionalString("hash").orEmpty()
        val range = args.optionalString("range").orEmpty()
        val path = args.optionalString("path").orEmpty()
        val opened = withContext(Dispatchers.EDT) {
            when (view) {
                "log" -> showLog(hash, range)
                "history" -> GitLogNavigator.showFileHistory(project, historyPath(path))
                "commit" -> showCommitWindow()
                "pull_requests" -> ForgeViewNavigator.open(project)
                else -> throw ToolException("view must be log, history, commit or pull_requests")
            }
        }
        if (!opened) throw ToolException(MISSING.getValue(view))
        return ToolResult.toon(
            buildJsonObject {
                put("view", view)
                put("opened", true)
                put("hash", hash)
                put("range", range)
                put("path", path)
            },
        )
    }

    private fun showLog(hash: String, range: String): Boolean = when {
        range.isNotEmpty() -> refRange(range).let { (exclusive, inclusive) -> GitLogNavigator.showRange(project, exclusive, inclusive) }
        hash.isNotEmpty() -> GitLogNavigator.showCommit(project, commitHash(hash))
        else -> GitLogNavigator.showLog(project)
    }

    private fun showCommitWindow(): Boolean {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.COMMIT) ?: return false
        toolWindow.activate(null, true)
        return true
    }

    private fun commitHash(hash: String): String {
        if (!HASH.matches(hash)) throw ToolException("hash must be $MIN_HASH_LENGTH to $MAX_HASH_LENGTH hexadecimal characters")
        return hash
    }

    private fun historyPath(path: String): String {
        if (path.isEmpty()) throw ToolException("view=history needs path")
        return VcsPaths.base(project).resolve(path).normalize().toString()
    }

    private suspend fun action(args: ToolArgs): ToolResult {
        val name = args.string("action")
        val id = ACTIONS[name] ?: throw ToolException("action must be one of ${ACTIONS.keys.joinToString()}")
        actions.dispatch(id)
        return ToolResult.toon(
            buildJsonObject {
                put("action", name)
                put("id", id)
                put("dispatched", true)
            },
        )
    }

    companion object {

        private const val MIN_HASH_LENGTH = 4
        private const val MAX_HASH_LENGTH = 64
        private val HASH = Regex("[0-9a-fA-F]{$MIN_HASH_LENGTH,$MAX_HASH_LENGTH}")
        private const val RANGE_SEPARATOR = ".."
        private const val HEAD = "HEAD"
        private val REF = Regex("[A-Za-z0-9_][A-Za-z0-9._/@{}~^-]*")

        fun refRange(range: String): Pair<String, String> {
            val refs = range.split(RANGE_SEPARATOR)
            val exclusive = refs.first()
            val inclusive = if (refs.size == 1) HEAD else refs[1]
            if (refs.size > 2 || !REF.matches(exclusive) || !REF.matches(inclusive)) {
                throw ToolException(
                    "range must be exclusive..inclusive, two refs or hashes as git log takes them; the second defaults to " + HEAD,
                )
            }
            return exclusive to inclusive
        }

        val ACTIONS: Map<String, String> = linkedMapOf(
            "pull" to "Git.Pull",
            "push" to "Vcs.Push",
            "fetch" to "Git.Fetch",
            "merge" to "Git.Merge",
            "rebase" to "Git.Rebase",
            "branches" to "Git.Branches",
            "stash" to "Git.Stash",
            "unstash" to "Git.Unstash",
            "tag" to "Git.Tag",
            "reset" to "Git.Reset",
            "resolve_conflicts" to "Git.ResolveConflicts",
            "commit" to "CheckinProject",
            "update" to "Vcs.UpdateProject",
            "create_pull_request" to "Github.Create.Pull.Request",
            "pull_requests" to "Github.View.Pull.Request",
            "create_merge_request" to "GitLab.Merge.Request.Create",
            "merge_requests" to "GitLab.Merge.Request.Show.List",
        )

        private val MISSING: Map<String, String> = mapOf(
            "log" to "the IDE has no Version Control tool window, this project is not a Git working copy, or the Git log is " +
                "still loading: retry in a moment",
            "history" to "the path is outside the project, or the IDE has no history for it",
            "commit" to "this IDE has no Commit tool window",
            "pull_requests" to "neither the GitHub nor the GitLab plugin is installed, so there is no requests view",
        )

        val VCS_OPEN = ToolSpec(
            "vcs_open",
            "Shows one of the IDE's VCS views: the Git log (at a commit when hash is given, or only the commits of a range), " +
                "a file's history, the Commit tool window, or the GitHub/GitLab pull or merge requests view. Use it to put what " +
                "you found in front of the user, and range to compare a branch with a tag or a release with the previous one.",
            listOf(
                Param("view", "log, history, commit or pull_requests"),
                Param("hash", "Commit to select in the log, 4 to 64 hex characters (view=log only)", required = false),
                Param(
                    "range",
                    "exclusive..inclusive as git log takes it, e.g. v1.2.0..HEAD; inclusive defaults to HEAD. Opens a log tab " +
                        "with only those commits (view=log only)",
                    required = false,
                ),
                Param("path", "File whose history to show, absolute or relative to the project root (view=history)", required = false),
            ),
        )

        val VCS_ACTION = ToolSpec(
            "vcs_action",
            "Opens one of the IDE's own Git, GitHub or GitLab dialogs (pull, push, fetch, merge, rebase, branches, stash, " +
                "unstash, tag, reset, resolve_conflicts, commit, update, create_pull_request, pull_requests, create_merge_request, " +
                "merge_requests) for the user to finish. It returns as soon as the dialog opens; nothing is changed until the " +
                "user confirms there.",
            listOf(Param("action", "One of the names above")),
            mutates = true,
        )
    }
}
