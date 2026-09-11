package dev.lain.claudejb.controller.mcp

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import dev.lain.claudejb.controller.git.GitHistoryService
import dev.lain.claudejb.model.mcp.ToolException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

internal class IdeActions(private val project: Project, private val scope: CoroutineScope) {

    suspend fun dispatch(actionId: String) {
        withContext(Dispatchers.EDT) {
            val action = ActionManager.getInstance().getAction(actionId)
                ?: throw ToolException("this IDE has no action $actionId; the plugin that provides it is not installed")
            val context = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.VIRTUAL_FILE, repositoryRoot())
                .build()
            val event = AnActionEvent.createEvent(action, context, null, ActionPlaces.TOOLWINDOW_CONTENT, ActionUiKind.TOOLBAR, null)
            ActionUtil.updateAction(action, event)
            if (!event.presentation.isEnabled || !event.presentation.isVisible) {
                throw ToolException("the IDE refused $actionId in this context: it is not enabled here")
            }
            scope.launch(Dispatchers.EDT) { ActionUtil.performAction(action, event) }
        }
    }

    private fun repositoryRoot(): VirtualFile? =
        project.service<GitHistoryService>().primaryRepositoryRoot()?.let { LocalFileSystem.getInstance().findFileByNioFile(Path.of(it)) }
}
