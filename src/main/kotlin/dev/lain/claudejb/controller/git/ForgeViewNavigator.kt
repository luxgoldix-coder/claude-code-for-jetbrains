package dev.lain.claudejb.controller.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

object ForgeViewNavigator {

    val TOOL_WINDOW_IDS: List<String> = listOf("Pull Requests", "Merge Requests")

    fun open(project: Project, focus: Boolean): Boolean {
        val toolWindow = found(project) ?: return false
        if (focus) toolWindow.activate(null, true) else toolWindow.show()
        return true
    }

    private fun found(project: Project) =
        TOOL_WINDOW_IDS.firstNotNullOfOrNull { ToolWindowManager.getInstance(project).getToolWindow(it) }
}
