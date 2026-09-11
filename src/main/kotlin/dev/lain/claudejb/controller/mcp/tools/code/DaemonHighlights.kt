package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class Highlight(val line: Int, val column: Int, val severity: String, val message: String, val inspection: String?)

internal class DaemonHighlights(private val project: Project) {

    suspend fun collect(file: VirtualFile, document: Document, minSeverity: HighlightSeverity?): List<Highlight>? {
        val analysed = CompletableDeferred<Unit>()
        val connection = project.messageBus.connect()
        try {
            val editors = withContext(Dispatchers.EDT) {
                val opened = FileEditorManager.getInstance(project).openFile(file, false).toList()
                connection.subscribe(
                    DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
                    object : DaemonCodeAnalyzer.DaemonListener {
                        override fun daemonFinished(fileEditors: Collection<FileEditor>) {
                            if (fileEditors.any { it in opened }) analysed.complete(Unit)
                        }
                    },
                )
                opened
            }
            if (editors.any { DaemonCodeAnalyzerEx.isHighlightingCompleted(it, project) }) analysed.complete(Unit)
            withTimeoutOrNull(ANALYSIS_TIMEOUT_MILLIS) { analysed.await() } ?: return null
        } finally {
            connection.disconnect()
        }
        return readAction {
            val out = ArrayList<Highlight>()
            DaemonCodeAnalyzerEx.processHighlights(document, project, minSeverity, 0, document.textLength) { info ->
                val description = info.description
                if (description != null) {
                    val line = document.getLineNumber(info.startOffset)
                    out += Highlight(
                        line + 1,
                        info.startOffset - document.getLineStartOffset(line) + 1,
                        info.severity.name,
                        description,
                        info.inspectionToolId,
                    )
                }
                true
            }
            out
        }
    }

    private companion object {
        const val ANALYSIS_TIMEOUT_MILLIS = 30_000L
    }
}
