package dev.lain.claudejb.controller.mcp

import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.git.GitAvailability
import dev.lain.claudejb.controller.mcp.tools.code.DiagnosticsTools
import dev.lain.claudejb.controller.mcp.tools.code.EditTools
import dev.lain.claudejb.controller.mcp.tools.code.EditorTools
import dev.lain.claudejb.controller.mcp.tools.code.FormatTools
import dev.lain.claudejb.controller.mcp.tools.code.HierarchyTools
import dev.lain.claudejb.controller.mcp.tools.code.InspectTools
import dev.lain.claudejb.controller.mcp.tools.code.NavigateTools
import dev.lain.claudejb.controller.mcp.tools.code.OutlineTools
import dev.lain.claudejb.controller.mcp.tools.code.ReadTools
import dev.lain.claudejb.controller.mcp.tools.code.RefactorTools
import dev.lain.claudejb.controller.mcp.tools.code.SearchTools
import dev.lain.claudejb.controller.mcp.tools.ops.ActionTools
import dev.lain.claudejb.controller.mcp.tools.ops.DbTools
import dev.lain.claudejb.controller.mcp.tools.ops.HttpTools
import dev.lain.claudejb.controller.mcp.tools.ops.IdeTools
import dev.lain.claudejb.controller.mcp.tools.ops.NotifyTools
import dev.lain.claudejb.controller.mcp.tools.ops.ProjectTools
import dev.lain.claudejb.controller.mcp.tools.ops.ServiceTools
import dev.lain.claudejb.controller.mcp.tools.ops.SshTools
import dev.lain.claudejb.controller.mcp.tools.run.BreakpointTools
import dev.lain.claudejb.controller.mcp.tools.run.BuildTools
import dev.lain.claudejb.controller.mcp.tools.run.DebugTools
import dev.lain.claudejb.controller.mcp.tools.run.RunTools
import dev.lain.claudejb.controller.mcp.tools.run.TerminalTools
import dev.lain.claudejb.controller.mcp.tools.run.TestTools
import dev.lain.claudejb.controller.mcp.tools.vcs.ForgeTools
import dev.lain.claudejb.controller.mcp.tools.vcs.GitReadTools
import dev.lain.claudejb.controller.mcp.tools.vcs.GitWriteTools
import dev.lain.claudejb.model.mcp.ToolCatalog
import dev.lain.claudejb.model.mcp.ToolDomain
import dev.lain.claudejb.model.session.launch.IdeServer
import kotlinx.coroutines.CoroutineScope

internal object IdeToolCatalog {

    private val DOMAINS: Map<IdeServer, List<(Project, CoroutineScope) -> ToolDomain?>> = mapOf(
        IdeServer.CODE to listOf(
            { p, _ -> ReadTools(p, Reveal(p)).domain() },
            { p, _ -> SearchTools(p).domain() },
            { p, _ -> NavigateTools(p).domain() },
            { p, _ -> OutlineTools(p).domain() },
            { p, _ -> DiagnosticsTools(p, Reveal(p)).domain() },
            { p, _ -> InspectTools(p).domain() },
            { p, _ -> EditTools(p, Reveal(p)).domain() },
            { p, _ -> RefactorTools(p).domain() },
            { p, _ -> FormatTools(p).domain() },
            { p, _ -> EditorTools(p, Reveal(p)).domain() },
            { p, _ -> HierarchyTools(p).domain() },
        ),
        IdeServer.RUN to listOf(
            { p, s -> BuildTools(p, s).domain() },
            { p, s -> RunTools(p, s).domain() },
            { p, s -> TestTools(p, s).domain() },
            { p, s -> TerminalTools(p, s).domain() },
            { p, _ -> DebugTools(p).domain() },
            { p, _ -> BreakpointTools(p).domain() },
        ),
        IdeServer.VCS to listOf(
            { p, _ -> GitReadTools(p, Reveal(p)).domain() },
            { p, _ -> GitWriteTools(p).domain() },
            { p, s -> ForgeTools(p, IdeActions(p, s), Reveal(p)).domain() },
        ),
        IdeServer.OPS to listOf(
            { p, s -> ServiceTools(p, s, Reveal(p)).domain() },
            { p, _ -> ProjectTools(p).domain() },
            { p, s -> IdeTools(p, IdeActions(p, s), s).domain() },
            { p, s -> ActionTools(p, IdeActions(p, s)).domain() },
            { p, _ -> NotifyTools(p).domain() },
            { p, _ -> DbTools(p).domain() },
            { p, s -> HttpTools(p, s, Reveal(p)).takeIf { it.available() }?.domain() },
            { p, _ -> SshTools(p).takeIf { it.available() }?.domain() },
        ),
    )

    private val REQUIRES: Map<IdeServer, () -> Boolean> = mapOf(IdeServer.VCS to GitAvailability::isGitPluginEnabled)

    fun catalog(server: IdeServer, project: Project, scope: CoroutineScope): ToolCatalog {
        if (REQUIRES[server]?.invoke() == false) return ToolCatalog(emptyList())
        return ToolCatalog(DOMAINS[server].orEmpty().mapNotNull { it(project, scope) })
    }
}
