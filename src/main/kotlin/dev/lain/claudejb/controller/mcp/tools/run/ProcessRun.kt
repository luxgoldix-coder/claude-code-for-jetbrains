package dev.lain.claudejb.controller.mcp.tools.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import dev.lain.claudejb.model.mcp.ToolException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class ProcessRun(private val project: Project) {

    suspend fun run(settings: RunnerAndConfigurationSettings, tail: OutputTail, onHandler: (ProcessHandler) -> Unit = {}): Int {
        val started = CompletableDeferred<Unit>()
        val exited = CompletableDeferred<Int>()
        val connection = project.messageBus.connect()
        try {
            connection.subscribe(ExecutionManager.EXECUTION_TOPIC, listener(settings, tail, onHandler, started, exited))
            withContext(Dispatchers.EDT) { launch(settings) }
            withTimeoutOrNull(START_TIMEOUT_MILLIS) { started.await() }
                ?: throw ToolException(
                    "${settings.name} did not start within ${START_TIMEOUT_MILLIS / MILLIS} s: a before-launch task may have " +
                        "failed, or the IDE is asking a question about it",
                )
            return exited.await()
        } finally {
            connection.disconnect()
        }
    }

    private fun launch(settings: RunnerAndConfigurationSettings) {
        val environment = try {
            ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), settings).activeTarget().build()
        } catch (e: ExecutionException) {
            throw ToolException("${settings.name} cannot run: ${e.message}", e)
        }
        ExecutionManager.getInstance(project).restartRunProfile(environment)
    }

    private fun listener(
        settings: RunnerAndConfigurationSettings,
        tail: OutputTail,
        onHandler: (ProcessHandler) -> Unit,
        started: CompletableDeferred<Unit>,
        exited: CompletableDeferred<Int>,
    ): ExecutionListener = object : ExecutionListener {

        override fun processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
            if (env.runnerAndConfigurationSettings !== settings) return
            handler.addProcessListener(
                object : ProcessListener {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        if (ProcessOutputType.isStdout(outputType) || ProcessOutputType.isStderr(outputType)) tail.text(event.text)
                    }
                },
            )
            onHandler(handler)
            started.complete(Unit)
        }

        override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
            if (env.runnerAndConfigurationSettings === settings) exited.complete(exitCode)
        }

        override fun processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable?) {
            if (env.runnerAndConfigurationSettings !== settings) return
            val reason = cause?.message?.let { ": $it" } ?: ""
            started.completeExceptionally(ToolException("${settings.name} did not start$reason", cause))
        }
    }

    private companion object {
        const val MILLIS = 1000L
        const val START_TIMEOUT_MILLIS = 600_000L
    }
}
