package dev.lain.claudejb.controller.mcp.tools.run

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueContainer
import com.intellij.xdebugger.frame.XValueModifier
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import dev.lain.claudejb.model.mcp.ToolException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.swing.Icon

internal class RenderedText : ColoredTextContainer, XValuePresentation.XValueTextRenderer {

    private val out = StringBuilder()

    val text: String get() = out.toString()

    override fun append(fragment: String, attributes: SimpleTextAttributes) {
        out.append(fragment)
    }

    override fun renderValue(value: String) {
        out.append(value)
    }

    override fun renderValue(value: String, key: TextAttributesKey) {
        out.append(value)
    }

    override fun renderStringValue(value: String) {
        out.append('"').append(value).append('"')
    }

    override fun renderStringValue(value: String, additionalSpecialCharsToHighlight: String?, maxLength: Int) {
        renderStringValue(if (value.length > maxLength) value.take(maxLength) + "…" else value)
    }

    override fun renderNumericValue(value: String) {
        out.append(value)
    }

    override fun renderKeywordValue(value: String) {
        out.append(value)
    }

    override fun renderComment(comment: String) {
        out.append(' ').append(comment)
    }

    override fun renderSpecialSymbol(symbol: String) {
        out.append(symbol)
    }

    override fun renderError(error: String) {
        out.append(error)
    }
}

internal class Presented(val type: String, val value: String, val hasChildren: Boolean)

internal object DebugValues {

    private const val CALLBACK_TIMEOUT_MILLIS = 10_000L
    private const val MILLIS = 1000L

    suspend fun stackFrames(stack: XExecutionStack, max: Int): List<XStackFrame> = collected { done ->
        val frames = ArrayList<XStackFrame>()
        stack.computeStackFrames(
            0,
            object : XExecutionStack.XStackFrameContainer {
                override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
                    frames += stackFrames
                    if (last || frames.size >= max) done.complete(frames.take(max))
                }

                override fun errorOccurred(errorMessage: String) {
                    done.completeExceptionally(ToolException(errorMessage))
                }

                override fun isObsolete(): Boolean = done.isCompleted
            },
        )
    }

    suspend fun children(container: XValueContainer, max: Int): List<Pair<String, XValue>> = collected { done ->
        val found = ArrayList<Pair<String, XValue>>()
        container.computeChildren(
            object : XCompositeNode {
                override fun addChildren(children: XValueChildrenList, last: Boolean) {
                    children.topValues.forEach { found += it.name to it }
                    for (i in 0 until children.size()) found += children.getName(i) to children.getValue(i)
                    if (last || found.size >= max) done.complete(found.take(max))
                }

                override fun tooManyChildren(remaining: Int) {
                    done.complete(found.take(max))
                }

                override fun setAlreadySorted(alreadySorted: Boolean) = Unit

                override fun setErrorMessage(errorMessage: String) {
                    done.completeExceptionally(ToolException(errorMessage))
                }

                override fun setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink?) = setErrorMessage(errorMessage)

                override fun setMessage(
                    message: String,
                    icon: Icon?,
                    attributes: SimpleTextAttributes,
                    link: XDebuggerTreeNodeHyperlink?,
                ) = Unit

                override fun isObsolete(): Boolean = done.isCompleted
            },
        )
    }

    suspend fun present(target: XValue): Presented = collected { done ->
        target.computePresentation(
            object : XValueNode {
                override fun setPresentation(icon: Icon?, type: String?, value: String, hasChildren: Boolean) {
                    done.complete(Presented(type ?: "", value, hasChildren))
                }

                override fun setPresentation(icon: Icon?, presentation: XValuePresentation, hasChildren: Boolean) {
                    val rendered = RenderedText().also(presentation::renderValue)
                    done.complete(Presented(presentation.type ?: "", rendered.text, hasChildren))
                }

                override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) = Unit

                override fun isObsolete(): Boolean = done.isCompleted
            },
            XValuePlace.TREE,
        )
    }

    suspend fun rows(children: List<Pair<String, XValue>>): List<JsonObject> = children.map { (name, value) ->
        val shown = present(value)
        buildJsonObject {
            put("name", name)
            put("type", shown.type)
            put("value", shown.value)
            put("children", shown.hasChildren)
        }
    }

    suspend fun evaluate(frame: XStackFrame, code: String): XValue {
        val evaluator = frame.evaluator ?: throw ToolException("this frame cannot evaluate expressions")
        return collected { done ->
            evaluator.evaluate(
                code,
                object : XDebuggerEvaluator.XEvaluationCallback {
                    override fun evaluated(result: XValue) {
                        done.complete(result)
                    }

                    override fun errorOccurred(errorMessage: String) {
                        done.completeExceptionally(ToolException(errorMessage))
                    }
                },
                frame.sourcePosition,
            )
        }
    }

    suspend fun set(target: XValue, name: String, text: String) {
        val modifier = target.modifier ?: throw ToolException("$name is read-only here")
        val expression = XDebuggerUtil.getInstance().createExpression(text, null, null, EvaluationMode.EXPRESSION)
        collected<Unit> { done ->
            modifier.setValue(
                expression,
                object : XValueModifier.XModificationCallback {
                    override fun valueModified() {
                        done.complete(Unit)
                    }

                    override fun errorOccurred(errorMessage: String) {
                        done.completeExceptionally(ToolException(errorMessage))
                    }
                },
            )
        }
    }

    private suspend fun <T> collected(start: (CompletableDeferred<T>) -> Unit): T {
        val done = CompletableDeferred<T>()
        start(done)
        return withTimeoutOrNull(CALLBACK_TIMEOUT_MILLIS) { done.await() }
            ?: throw ToolException("the debugger did not answer within ${CALLBACK_TIMEOUT_MILLIS / MILLIS} s; is the session paused?")
    }
}
