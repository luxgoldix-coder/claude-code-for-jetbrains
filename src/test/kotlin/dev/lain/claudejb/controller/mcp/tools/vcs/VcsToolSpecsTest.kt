package dev.lain.claudejb.controller.mcp.tools.vcs

import dev.lain.claudejb.controller.mcp.tools.code.DiagnosticsTools
import dev.lain.claudejb.model.mcp.ToolSpec
import dev.lain.claudejb.model.permission.scan.ToolInputScanner
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VcsToolSpecsTest {

    private val read = listOf(GitReadTools.GIT_STATUS, GitReadTools.GIT_LOG, GitReadTools.GIT_DIFF, GitReadTools.GIT_BRANCHES)
    private val write = listOf(GitWriteTools.GIT_STAGE, GitWriteTools.GIT_COMMIT, GitWriteTools.GIT_BRANCH, GitWriteTools.GIT_REMOTE)
    private val forge = listOf(ForgeTools.VCS_OPEN, ForgeTools.VCS_ACTION)
    private val all = read + write + forge + DiagnosticsTools.PROBLEMS_VIEW

    @Test
    fun `the tool names are pinned per domain`() {
        assertEquals(listOf("git_status", "git_log", "git_diff", "git_branches"), read.map { it.name })
        assertEquals(listOf("git_stage", "git_commit", "git_branch", "git_remote"), write.map { it.name })
        assertEquals(listOf("vcs_open", "vcs_action"), forge.map { it.name })
        assertEquals("problems_view", DiagnosticsTools.PROBLEMS_VIEW.name)
        assertEquals(all.size, all.map { it.name }.toSet().size, "two vcs tools share a name")
    }

    @Test
    fun `what writes says so, and what reads does not`() {
        (write + ForgeTools.VCS_ACTION).forEach { assertTrue(it.mutates, "${it.name} mutates and must say so") }
        (read + ForgeTools.VCS_OPEN + DiagnosticsTools.PROBLEMS_VIEW).forEach { assertFalse(it.mutates, "${it.name} is read-only") }
    }

    @Test
    fun `no parameter is spelled like a shell command, so the guard never tokenises a branch name as one`() {
        all.flatMap { spec -> spec.params.map { spec.name to it.name } }.forEach { (tool, param) ->
            assertNull(ToolInputScanner.commandText(buildJsonObject { put(param, "x") }), "$tool.$param reads as a command key")
        }
    }

    @Test
    fun `a filesystem location is always called path or paths, which is what the guard walks`() {
        val locations = all.flatMap { spec -> spec.params.filter { LOCATION.containsMatchIn(it.description) }.map { spec.name to it.name } }
        assertEquals(
            listOf("git_diff" to "path", "git_stage" to "paths", "git_commit" to "paths", "vcs_open" to "path"),
            locations,
        )
        all.flatMap { it.params }.map { it.name }.forEach { assertFalse(it in ALIASES, "$it is a location under another name") }
    }

    @Test
    fun `git_branch has no delete action, because deleting a branch is the user's`() {
        assertFalse("delete" in GitWriteTools.GIT_BRANCH.params.first { it.name == "action" }.description)
        assertTrue("create or checkout" in GitWriteTools.GIT_BRANCH.params.first { it.name == "action" }.description)
    }

    @Test
    fun `the forge actions are a pinned table of verified IDE action ids`() {
        assertEquals(
            linkedMapOf(
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
            ),
            ForgeTools.ACTIONS,
        )
        ForgeTools.ACTIONS.keys.forEach { assertTrue(it in ForgeTools.VCS_ACTION.description, "vcs_action does not list $it") }
    }

    @Test
    fun `every integer parameter names its default`() {
        all.flatMap { spec -> spec.params.filter { it.type == "integer" }.map { spec.name to it } }.forEach { (tool, param) ->
            assertTrue("default" in param.description, "$tool.${param.name} names no default")
        }
    }

    @Test
    fun `the remote tool waits longer than the rest, and only that one`() {
        assertTrue(GitWriteTools.GIT_REMOTE.timeoutMillis > ToolSpec.DEFAULT_TIMEOUT_MILLIS)
        (all - GitWriteTools.GIT_REMOTE).forEach { assertEquals(ToolSpec.DEFAULT_TIMEOUT_MILLIS, it.timeoutMillis, it.name) }
    }

    private companion object {
        val LOCATION = Regex("""\b(file|files|director)""", RegexOption.IGNORE_CASE)
        val ALIASES = setOf("file", "files", "dir", "directory", "location", "target", "filename")
    }
}
