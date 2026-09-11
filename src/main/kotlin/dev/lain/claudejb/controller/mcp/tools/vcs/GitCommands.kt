package dev.lain.claudejb.controller.mcp.tools.vcs

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VfsUtil
import dev.lain.claudejb.controller.git.GitAvailability
import dev.lain.claudejb.controller.git.GitHistoryService
import dev.lain.claudejb.model.mcp.ToolException
import git4idea.checkin.GitCheckinEnvironment
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils

internal class GitCommands(private val project: Project) {

    private fun requireGit() {
        if (!GitAvailability.isGitPluginEnabled()) throw ToolException("the Git plugin (Git4Idea) is disabled in this IDE")
    }

    fun stage(paths: List<FilePath>) {
        requireGit()
        val repository = repository()
        vcs { GitFileUtils.addPaths(project, repository.root, paths) }
        refresh(repository, worktree = false)
    }

    fun unstage(paths: List<FilePath>) {
        requireGit()
        val repository = repository()
        vcs { GitFileUtils.resetPaths(project, repository.root, paths) }
        refresh(repository, worktree = false)
    }

    fun commit(message: String, paths: List<FilePath>, amend: Boolean) {
        requireGit()
        val repository = repository()
        if (paths.isNotEmpty()) vcs { GitFileUtils.addPaths(project, repository.root, paths) }
        val messageFile = GitCheckinEnvironment.createCommitMessageFile(project, repository.root, message)
        val handler = GitLineHandler(project, repository.root, GitCommand.COMMIT)
        if (amend) handler.addParameters("--amend")
        handler.addParameters("-F")
        handler.addAbsoluteFile(messageFile)
        if (paths.isNotEmpty()) handler.addParameters("--only")
        handler.endOptions()
        handler.addRelativePaths(paths)
        checked(Git.getInstance().runCommand(handler))
        refresh(repository, worktree = false)
    }

    fun createBranch(name: String, startPoint: String) {
        requireGit()
        val repository = repository()
        checked(Git.getInstance().branchCreate(repository, name, startPoint))
        refresh(repository, worktree = false)
    }

    fun checkout(reference: String, newBranch: String?) {
        requireGit()
        val repository = repository()
        checked(Git.getInstance().checkout(repository, reference, newBranch, false, false))
        refresh(repository, worktree = true)
    }

    fun fetch(remoteName: String?): String {
        requireGit()
        val repository = repository()
        val remote = remote(repository, remoteName)
        checked(Git.getInstance().fetch(repository, remote, emptyList()))
        refresh(repository, worktree = false)
        return remote.name
    }

    fun pull(remoteName: String?, branch: String): String {
        requireGit()
        val repository = repository()
        val remote = remote(repository, remoteName)
        val handler = GitLineHandler(project, repository.root, GitCommand.PULL)
        remote.firstUrl?.let(handler::setUrl)
        handler.addParameters(remote.name, branch)
        checked(Git.getInstance().runCommand(handler))
        refresh(repository, worktree = true)
        return remote.name
    }

    fun push(remoteName: String?, branch: String): String {
        requireGit()
        val repository = repository()
        val remote = remote(repository, remoteName)
        val setUpstream = repository.getBranchTrackInfo(branch) == null
        checked(Git.getInstance().push(repository, remote.name, remote.firstUrl, "$branch:$branch", setUpstream))
        refresh(repository, worktree = false)
        return remote.name
    }

    private fun repository(): GitRepository {
        val wanted = project.service<GitHistoryService>().primaryRepositoryRoot()
        return GitRepositoryManager.getInstance(project).repositories.firstOrNull { it.root.path == wanted }
            ?: throw ToolException("this project is not a Git working copy")
    }

    private fun remote(repository: GitRepository, name: String?): GitRemote {
        val remotes = repository.remotes
        val chosen = if (name == null) {
            remotes.firstOrNull { it.name == GitRemote.ORIGIN } ?: remotes.singleOrNull()
        } else {
            remotes.firstOrNull { it.name == name }
        }
        val wanted = name ?: GitRemote.ORIGIN
        val known = remotes.joinToString { it.name }
        return chosen ?: throw ToolException(
            if (remotes.isEmpty()) "this repository has no remote" else "no remote named $wanted; the remotes are $known",
        )
    }

    private fun checked(result: GitCommandResult) {
        if (result.success()) return
        val text = result.errorOutputAsJoinedString.ifBlank { result.outputAsJoinedString }.ifBlank { "git failed" }
        throw ToolException(if (CONFLICT in text) "$text; $CONFLICT_HINT" else text)
    }

    private fun <T> vcs(block: () -> T): T = try {
        block()
    } catch (e: VcsException) {
        throw ToolException(e.message.ifBlank { "git failed" }, e)
    }

    private fun refresh(repository: GitRepository, worktree: Boolean) {
        repository.update()
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
        if (worktree) VfsUtil.markDirtyAndRefresh(false, true, false, repository.root)
    }

    companion object {
        private const val CONFLICT = "CONFLICT"
        private const val CONFLICT_HINT = "resolve the conflicts in the IDE with vcs_action(action=resolve_conflicts)"
    }
}
