package dev.lain.claudejb.session

import com.intellij.openapi.diagnostic.thisLogger
import dev.lain.claudejb.process.ClaudeProcess
import dev.lain.claudejb.protocol.ClaudeEvent
import dev.lain.claudejb.settings.ClaudeSettings
import java.io.File

class SessionProcess(
    private val s: ClaudeSession,
    private val edt: (() -> Unit) -> Unit,
    private val fireState: () -> Unit,
    private val fireAttention: (AttentionReason) -> Unit,
    private val onEvent: (ClaudeEvent) -> Unit,
) {

    private val log = thisLogger()

    @Volatile private var process: ClaudeProcess? = null

    @Volatile var generation = 0
        private set

    @Volatile private var resumedLaunch = false

    fun isRunning(): Boolean = process?.isRunning() == true

    fun write(line: String): Boolean = process?.writeLine(line) ?: false

    fun supersede(): Int = ++generation

    fun terminate() {
        process?.terminate()
        process = null
    }

    fun spawn(
        launchGen: Int,
        settings: ClaudeSettings,
        binary: File,
        workDir: File,
        env: Map<String, String>,
        resume: Boolean,
    ): Boolean {
        if (launchGen != generation) return false
        resumedLaunch = resume
        val opts = s.launch.copy(sessionId = s.sessionId)
        val proc = ClaudeProcess(
            binary = binary,
            workDir = workDir,
            args = SessionLauncher.buildArgs(opts, resume, SessionLauncher.mcpConfigJson(opts)),
            nodeOverride = settings.nodePath,
            extraEnv = env,
            onEvent = onEvent,
            onTerminated = { code -> onTerminated(launchGen, code) },
        )
        process = proc
        val started = runCatching { proc.start() }
        if (started.isFailure) {
            process = null
            log.warn("Failed to start the claude process", started.exceptionOrNull())
            s.notifier.error("Failed to start Claude Code: ${started.exceptionOrNull()?.message ?: "unknown error"}")
            return false
        }
        if (launchGen != generation) {
            proc.terminate()
            if (process === proc) process = null
            return false
        }
        return true
    }

    private fun onTerminated(gen: Int, exitCode: Int) {
        if (gen != generation) return
        val staleResume = resumedLaunch && !s.catalog.initialized
        s.flushDeltas()
        s.controlClient.failAll("process gone")
        edt {
            s.turn.reset()
            s.lifecycle.ready = false
            s.catalog.initialized = false
            s.prompts.dropSuggestion()
            s.cardManager.clear()
            s.taskTracker.clear()
            s.hookNarrator.clear()
            when {
                exitCode != 0 && staleResume -> restartFresh(exitCode)

                exitCode != 0 -> {
                    s.transcript.add(Speaker.ERROR, "Claude Code exited (code $exitCode).")
                    s.notifier.error("Claude Code exited unexpectedly (code $exitCode).")
                    fireAttention(AttentionReason.ERROR)
                    fireState()
                }

                else -> {
                    s.systemNotice("Session ended.")
                    fireState()
                }
            }
        }
    }

    private fun restartFresh(exitCode: Int) {
        log.info("resume of session ${s.sessionId} failed (exit $exitCode) — continuing as a new conversation")
        s.sessionId = null
        resumedLaunch = false
        s.systemNotice("That conversation is no longer available — started a new one.")
        fireState()
        s.start(resume = false)
    }
}
