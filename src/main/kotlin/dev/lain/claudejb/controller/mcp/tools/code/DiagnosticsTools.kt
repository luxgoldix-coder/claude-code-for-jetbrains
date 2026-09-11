package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class DiagnosticsTools(private val project: Project, private val daemon: DaemonHighlights = DaemonHighlights(project)) {

    fun domain(): ToolDomain = ToolDomain(
        "diagnostics",
        "What the IDE's own analysis flags: the highlights of one file, or the Problems view for the whole project",
        listOf(Tool(PROBLEMS, ::problems), Tool(PROJECT_PROBLEMS, ::projectProblems)),
    )

    private suspend fun problems(args: ToolArgs): ToolResult {
        val path = args.string("path")
        val severity = severity(args.optionalString("severity") ?: "warning")
        val max = args.int("max", DEFAULT_MAX)
        val (file, document) = readAction {
            val file = ReadTools.resolveFile(project, path)
            file to Locations.document(project, file)
        }
        val highlights = daemon.collect(file, document, severity)
            ?: throw ToolException("the IDE has not finished analysing $path; retry in a moment")
        return ToolResult.toon(
            buildJsonObject {
                put("path", path)
                put("count", highlights.size)
                put("truncated", highlights.size > max)
                put(
                    "problems",
                    buildJsonArray {
                        highlights.take(max).forEach { h ->
                            add(
                                buildJsonObject {
                                    put("line", h.line)
                                    put("column", h.column)
                                    put("severity", h.severity)
                                    put("message", h.message)
                                    h.inspection?.let { put("inspection", it) }
                                },
                            )
                        }
                    },
                )
            },
        )
    }

    private suspend fun projectProblems(args: ToolArgs): ToolResult {
        val max = args.int("max", DEFAULT_MAX)
        val (rows, total) = withContext(Dispatchers.EDT) {
            val collector = ProblemsCollector.getInstance(project)
            val rows = ArrayList<JsonObject>()
            for (file in collector.getProblemFiles()) {
                for (problem in collector.getFileProblems(file)) {
                    if (rows.size >= max) break
                    rows += row(problem)
                }
            }
            for (problem in collector.getOtherProblems()) {
                if (rows.size >= max) break
                rows += row(problem)
            }
            rows to collector.getProblemCount()
        }
        return ToolResult.toon(
            buildJsonObject {
                put("count", total)
                put("truncated", total > rows.size)
                put("problems", buildJsonArray { rows.forEach { add(it) } })
            },
        )
    }

    private fun row(problem: Problem): JsonObject = buildJsonObject {
        if (problem is FileProblem) {
            put("file", Locations.relative(project, problem.file))
            if (problem.line >= 0) put("line", problem.line + 1)
            if (problem.column >= 0) put("column", problem.column + 1)
        }
        problem.group?.let { put("group", it) }
        put("message", problem.text)
    }

    private fun severity(name: String): HighlightSeverity? = when (name.lowercase()) {
        "error" -> HighlightSeverity.ERROR
        "warning" -> HighlightSeverity.WARNING
        "weak" -> HighlightSeverity.WEAK_WARNING
        "all" -> null
        else -> throw ToolException("severity must be error, warning, weak or all")
    }

    companion object {

        private const val DEFAULT_MAX = 100

        val PROBLEMS = ToolSpec(
            "problems",
            "The errors and warnings the IDE's analysis shows for one file, with line, column, severity and the inspection " +
                "that raised each. Opens the file in an editor tab, since the IDE analyses open files.",
            listOf(
                Param("path", "File path, absolute or relative to the project root"),
                Param("severity", "Minimum severity: error, warning (default), weak or all", required = false),
                Param("max", "Maximum problems to return (default $DEFAULT_MAX)", type = "integer", required = false),
            ),
        )

        val PROJECT_PROBLEMS = ToolSpec(
            "project_problems",
            "Everything the Problems view lists right now across the project: file problems with their positions, " +
                "and problems with no file.",
            listOf(Param("max", "Maximum problems to return (default $DEFAULT_MAX)", type = "integer", required = false)),
        )
    }
}
