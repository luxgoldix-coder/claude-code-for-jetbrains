package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.mcp.Reveal
import dev.lain.claudejb.model.mcp.Batch
import dev.lain.claudejb.model.mcp.Param
import dev.lain.claudejb.model.mcp.Tool
import dev.lain.claudejb.model.mcp.ToolArgs
import dev.lain.claudejb.model.mcp.ToolDomain
import dev.lain.claudejb.model.mcp.ToolException
import dev.lain.claudejb.model.mcp.ToolResult
import dev.lain.claudejb.model.mcp.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class EditorTools(private val project: Project, private val reveal: Reveal) {

    fun domain(): ToolDomain = ToolDomain(
        "editor",
        "What the editor shows and whether the index is ready: open a file at a line, the active file and caret, indexing state",
        listOf(
            Tool(OPEN_FILE) { ToolResult.toon(Batch.run(it, Batch.PATHS, ::openOne)) },
            Tool(ACTIVE_FILE, ::activeFile),
            Tool(INDEX_STATUS, ::indexStatus),
        ),
    )

    private suspend fun openOne(args: ToolArgs): JsonObject {
        val path = args.string("path")
        val line = args.int("line", 1)
        val column = args.int("column", 1)
        if (line < 1 || column < 1) throw ToolException("line and column start at 1")
        val file = readAction { Locations.file(project, path) }
        val opened = reveal.file(file, line, column)
        return buildJsonObject {
            put("path", path)
            put("line", line)
            put("column", column)
            put("opened", opened)
        }
    }

    private suspend fun activeFile(ignored: ToolArgs): ToolResult = withContext(Dispatchers.EDT) {
        val manager = FileEditorManager.getInstance(project)
        val editor = manager.selectedTextEditor
        val file = editor?.let { FileDocumentManager.getInstance().getFile(it.document) }
        val caret = editor?.caretModel?.logicalPosition
        ToolResult.toon(
            buildJsonObject {
                put("file", file?.let { Locations.relative(project, it) } ?: "")
                put("line", caret?.line?.plus(1) ?: 0)
                put("column", caret?.column?.plus(1) ?: 0)
                put("selected", editor?.selectionModel?.selectedText?.take(SELECTION_CHARS) ?: "")
                put("open", buildJsonArray { manager.openFiles.forEach { add(JsonPrimitive(Locations.relative(project, it))) } })
            },
        )
    }

    private suspend fun indexStatus(args: ToolArgs): ToolResult {
        val wait = args.boolean("wait", false)
        val dumb = DumbService.getInstance(project).isDumb
        val indexing = if (dumb && wait) smartReadAction(project) { DumbService.getInstance(project).isDumb } else dumb
        return ToolResult.toon(
            buildJsonObject {
                put("indexing", indexing)
                put("waited", dumb && wait)
            },
        )
    }

    companion object {

        private const val SELECTION_CHARS = 200

        val OPEN_FILE = ToolSpec(
            "open_file",
            "Opens a file in an editor tab and places its caret at a line and column, as the IDE's Go to File does, " +
                "without taking the focus from where the user is working.",
            listOf(
                Param("path", "File path, absolute or relative to the project root", required = false),
                Batch.paths("each opens in its own tab"),
                Param("line", "1-based line for the caret (default 1)", type = "integer", required = false),
                Param("column", "1-based column for the caret (default 1)", type = "integer", required = false),
            ),
        )

        val ACTIVE_FILE = ToolSpec(
            "active_file",
            "The file in the selected editor with its caret position and selection, plus every open file. " +
                "Empty fields when no text editor is selected.",
        )

        val INDEX_STATUS = ToolSpec(
            "index_status",
            "Whether the IDE is still indexing; with wait, returns once indexing finishes so symbol tools can be trusted.",
            listOf(Param("wait", "true to wait for indexing to finish (default false)", type = "boolean", required = false)),
        )
    }
}
