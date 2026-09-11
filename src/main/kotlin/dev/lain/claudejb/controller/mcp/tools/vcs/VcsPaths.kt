package dev.lain.claudejb.controller.mcp.tools.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.vcsUtil.VcsUtil
import dev.lain.claudejb.model.git.GitCommitInfo
import dev.lain.claudejb.model.mcp.ToolException
import java.nio.file.Files
import java.nio.file.Path

internal object VcsPaths {

    fun filePath(project: Project, path: String): FilePath {
        val base = base(project)
        val absolute = Path.of(path).let { if (it.isAbsolute) it else base.resolve(it) }.normalize()
        if (!absolute.startsWith(base)) throw ToolException("$path is outside the project; only paths under $base are accepted")
        return VcsUtil.getFilePath(absolute, Files.isDirectory(absolute))
    }

    fun base(project: Project): Path = Path.of(project.basePath ?: throw ToolException("this project has no directory on disk"))

    fun relative(root: String, filePath: FilePath): String = GitCommitInfo.relativize(root, filePath.path)
}
