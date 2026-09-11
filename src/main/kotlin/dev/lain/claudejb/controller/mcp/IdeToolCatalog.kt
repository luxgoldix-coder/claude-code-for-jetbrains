package dev.lain.claudejb.controller.mcp

import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.mcp.tools.code.DiagnosticsTools
import dev.lain.claudejb.controller.mcp.tools.code.InspectTools
import dev.lain.claudejb.controller.mcp.tools.code.NavigateTools
import dev.lain.claudejb.controller.mcp.tools.code.OutlineTools
import dev.lain.claudejb.controller.mcp.tools.code.ReadTools
import dev.lain.claudejb.controller.mcp.tools.code.SearchTools
import dev.lain.claudejb.model.mcp.ToolCatalog
import dev.lain.claudejb.model.mcp.ToolDomain
import dev.lain.claudejb.model.session.launch.IdeServer

internal object IdeToolCatalog {

    private val DOMAINS: Map<IdeServer, List<(Project) -> ToolDomain>> = mapOf(
        IdeServer.CODE to listOf(
            { ReadTools(it).domain() },
            { SearchTools(it).domain() },
            { NavigateTools(it).domain() },
            { OutlineTools(it).domain() },
            { DiagnosticsTools(it).domain() },
            { InspectTools(it).domain() },
        ),
    )

    fun catalog(server: IdeServer, project: Project): ToolCatalog =
        ToolCatalog(DOMAINS[server].orEmpty().map { it(project) })
}
