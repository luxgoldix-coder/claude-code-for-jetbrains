package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import dev.lain.claudejb.controller.mcp.tools.code.Locations.place
import dev.lain.claudejb.model.mcp.Param
import dev.lain.claudejb.model.mcp.Tool
import dev.lain.claudejb.model.mcp.ToolArgs
import dev.lain.claudejb.model.mcp.ToolDomain
import dev.lain.claudejb.model.mcp.ToolException
import dev.lain.claudejb.model.mcp.ToolResult
import dev.lain.claudejb.model.mcp.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class OutlineTools(private val project: Project) {

    fun domain(): ToolDomain = ToolDomain(
        "outline",
        "The structure of a file as the IDE's Structure view shows it, and what a symbol at a position is",
        listOf(Tool(FILE_OUTLINE, ::fileOutline), Tool(SYMBOL_INFO, ::symbolInfo)),
    )

    private suspend fun fileOutline(args: ToolArgs): ToolResult {
        val path = args.string("path")
        val depth = args.int("depth", DEFAULT_DEPTH)
        val psiFile = readAction { Locations.psiFile(project, path) }
        val builder = readAction { LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psiFile) }
            as? TreeBasedStructureViewBuilder
            ?: throw ToolException("the IDE has no structure view for $path")
        val items = withContext(Dispatchers.EDT) {
            val model = builder.createStructureViewModel(null)
            try {
                children(model.root, depth)
            } finally {
                Disposer.dispose(model)
            }
        }
        return ToolResult.toon(
            buildJsonObject {
                put("path", path)
                put("items", items)
            },
        )
    }

    private fun children(element: TreeElement, depth: Int): JsonArray = buildJsonArray {
        if (depth <= 0) return@buildJsonArray
        for (child in element.children) add(node(child, depth - 1))
    }

    private fun node(element: TreeElement, depth: Int): JsonObject = buildJsonObject {
        val presentation = element.presentation
        put("name", presentation.presentableText ?: "")
        presentation.locationString?.takeIf { it.isNotBlank() }?.let { put("detail", it) }
        val value = (element as? StructureViewTreeElement)?.value as? PsiElement
        if (value != null) {
            put("kind", kind(value))
            put("line", line(value))
        }
        val nested = children(element, depth)
        if (nested.isNotEmpty()) put("children", nested)
    }

    private suspend fun symbolInfo(args: ToolArgs): ToolResult = readAction {
        val declaration = Locations.declarationAt(project, args)
        ToolResult.toon(
            buildJsonObject {
                put("name", (declaration as? PsiNamedElement)?.name ?: declaration.text.take(SIGNATURE_CHARS))
                put("kind", kind(declaration))
                put("signature", signature(declaration))
                (declaration as? NavigationItem)?.presentation?.let { presentation ->
                    presentation.presentableText?.let { put("presentation", it) }
                    presentation.locationString?.takeIf { it.isNotBlank() }?.let { put("in", it) }
                }
                place(project, declaration)
            },
        )
    }

    private fun signature(element: PsiElement): String {
        val text = element.navigationElement.text ?: return ""
        val head = text.lineSequence().map { it.trim() }
            .firstOrNull { line -> line.isNotEmpty() && COMMENT_OR_ANNOTATION.none(line::startsWith) }
        return (head ?: text.trim()).take(SIGNATURE_CHARS)
    }

    private fun line(element: PsiElement): Int {
        val document = element.containingFile?.viewProvider?.document ?: return 0
        return document.getLineNumber(element.textOffset.coerceIn(0, document.textLength)) + 1
    }

    private fun kind(value: Any): String = value.javaClass.simpleName.removePrefix("Psi").removePrefix("Kt").removeSuffix("Impl")

    companion object {

        private const val DEFAULT_DEPTH = 3
        private const val SIGNATURE_CHARS = 200
        private val COMMENT_OR_ANNOTATION = listOf("/", "*", "@", "#")

        val FILE_OUTLINE = ToolSpec(
            "file_outline",
            "The declarations of a file as a tree — classes, functions, fields — with their lines, like the Structure view.",
            listOf(
                Param("path", "File path, absolute or relative to the project root"),
                Param("depth", "How many levels of nesting to return (default $DEFAULT_DEPTH)", type = "integer", required = false),
            ),
        )

        val SYMBOL_INFO = ToolSpec(
            "symbol_info",
            "What the symbol at a position is: its kind, name, declaring signature and where it is declared.",
            Locations.POSITION,
        )
    }
}
