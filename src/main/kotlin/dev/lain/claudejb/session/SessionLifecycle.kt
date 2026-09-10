package dev.lain.claudejb.session

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import dev.lain.claudejb.process.ClaudeBinaryLocator
import dev.lain.claudejb.process.ClaudeProcess
import dev.lain.claudejb.process.CredentialsVault
import dev.lain.claudejb.protocol.ClaudeEvent
import dev.lain.claudejb.settings.ClaudeSettings
import dev.lain.claudejb.settings.Provider
import dev.lain.claudejb.settings.RemoteMounts
import dev.lain.claudejb.settings.SecretStore
import dev.lain.claudejb.settings.resolveEnv
import java.io.File

class SessionLifecycle(
    private val s: ClaudeSession,
    private val project: Project,
    private val edt: (() -> Unit) -> Unit,
    private val fireState: () -> Unit,
    private val fireAttention: (AttentionReason) -> Unit,
    private val onEvent: (ClaudeEvent) -> Unit,
) {

    private val log = thisLogger()

    @Volatile private var process: ClaudeProcess? = null

    @Volatile private var generation = 0

    @Volatile private var starting = false

    @Volatile private var resumedLaunch = false

    @Volatile internal var ready = false

    @Volatile internal var cachedEnv: Map<String, String>? = null

    @Volatile var binaryMissing: Boolean = false
        private set

    @Volatile var needsLogin: Boolean = false
        internal set

    val auth = AuthGate(
        project = project,
        signInInProgress = { s.login.inProgress },
        launchEnv = { effectiveLaunchEnv() },
        onProbed = { loggedIn ->
            when {
                !loggedIn -> onLoginNeeded()

                needsLogin -> {
                    needsLogin = false
                    edt { fireState() }
                }

                else -> edt { fireState() }
            }
        },
    )

    fun isRunning(): Boolean = process?.isRunning() == true

    fun isStarting(): Boolean = starting

    fun write(line: String): Boolean = process?.writeLine(line) ?: false

    fun start(resume: Boolean): Boolean {
        if (isRunning() || starting) return true
        val settings = ClaudeSettings.getInstance(project)
        val binary = resolveBinary(settings) ?: return false
        if (!passesLaunchGates(settings)) return false
        when (auth.heldCredential(settings)) {
            Credential.NONE -> {
                onLoginNeeded()
                return false
            }

            Credential.UNKNOWN -> return true

            Credential.HELD -> Unit
        }
        val workDir = project.basePath?.let(::File) ?: File(System.getProperty("user.home"))

        ready = false
        s.catalog.initialized = false
        starting = true
        s.reconciler.onMessageBoundary()
        fireState()
        val launchGen = ++generation

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                launch(launchGen, settings, binary, workDir, resume)
            } finally {
                if (launchGen == generation) {
                    starting = false
                    edt { fireState() }
                }
            }
        }
        return true
    }

    internal fun onLoginNeeded() {
        needsLogin = true
        edt { fireState() }
        s.login.maybePrompt()
    }

    fun refreshBootState() {
        if (starting) return
        if (s.login.inProgress) return
        auth.absorbExistingLoginOnce()
        val settings = ClaudeSettings.getInstance(project)
        val binary = ClaudeBinaryLocator.locate(settings.claudePath)
        val missing = binary == null
        edt {
            if (missing && isRunning()) stop()
            if (missing != binaryMissing) {
                binaryMissing = missing
                fireState()
            }
        }
        if (binary == null) return
        if (settings.claudePath != binary.absolutePath) {
            settings.update { it.claudePath = binary.absolutePath }
        }
        val credentialed = auth.hasCredential(settings)
        edt {
            if (starting) return@edt
            when {
                !credentialed -> {
                    if (isRunning()) stop()
                    if (!needsLogin) onLoginNeeded()
                }

                !isRunning() -> s.start()
            }
        }
    }

    fun dismissLoginCard() {
        needsLogin = false
        edt { fireState() }
    }

    private fun resolveBinary(settings: ClaudeSettings): File? {
        val binary = ClaudeBinaryLocator.locate(settings.claudePath) ?: run {
            binaryMissing = true
            fireState()
            s.notifier.missingBinary()
            return null
        }
        binaryMissing = false
        if (settings.claudePath != binary.absolutePath) {
            settings.update { it.claudePath = binary.absolutePath }
        }
        return binary
    }

    private fun passesLaunchGates(settings: ClaudeSettings): Boolean {
        if (!s.notifier.ensureExecTrust(settings)) return false
        if (RemoteMounts.isRemote(project.basePath)) {
            refuseRemoteProject(project.basePath)
            return false
        }
        return true
    }

    private fun refuseRemoteProject(root: String?) {
        val msg = SessionNotifier.remoteProjectRefusal(root)
        edt {
            s.transcript.add(Speaker.ERROR, msg)
            fireState()
        }
        s.notifier.error(msg)
        starting = false
    }

    internal fun effectiveLaunchEnv(base: Map<String, String>? = null): Map<String, String> {
        val env = base ?: ClaudeSettings.getInstance(project).resolveEnv()
        val settings = ClaudeSettings.getInstance(project)
        val apiKey = settings.anthropicApiKey
            .takeIf { it.isNotBlank() && settings.provider == Provider.ANTHROPIC && SecretStore.API_KEY !in env }
        val withSecrets = env +
            SecretStore.envOverlay(env.keys) +
            (apiKey?.let { mapOf(SecretStore.API_KEY to it) } ?: emptyMap())
        return withSecrets + CredentialsVault.envOverlay(withSecrets.keys)
    }

    private fun launch(launchGen: Int, settings: ClaudeSettings, binary: File, workDir: File, resume: Boolean) {
        if (!auth.renew(binary, settings)) {
            edt { onLoginNeeded() }
            return
        }
        val env = effectiveLaunchEnv(cachedEnv ?: settings.resolveEnv().also { cachedEnv = it })
        if (launchGen != generation) return
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
            return
        }
        if (launchGen != generation) {
            proc.terminate()
            if (process === proc) process = null
            return
        }
        s.catalog.request()
        edt {
            ready = true
            s.transcript.add(Speaker.SYSTEM, "Claude Code ready.")
            auth.probe()
            fireState()
            s.poll.pollQuota()
            s.prompts.pump()
        }
    }

    fun restart(resume: Boolean) {
        stop()
        s.start(resume)
    }

    fun stop() {
        generation++
        s.flushDeltas()
        s.turnControl.cancelPendingElicitations()
        process?.terminate()
        process = null
        s.turn.reset()
        ready = false
        s.catalog.initialized = false
        starting = false
        s.prompts.dropSuggestion()
        cachedEnv = null
        s.controlClient.failAll("process gone")
        s.taskTracker.clear()
        s.hookNarrator.clear()
        s.backgroundTaskRegistry.clear()
        s.agentScanner.clearTails()
        edt {
            s.cardManager.clear()
            s.diffs.clearReviewDiffs()
            fireState()
        }
    }

    fun shutdown() {
        generation++
        starting = false
        s.poll.stopAll()
        s.turnControl.cancelPendingElicitations()
        s.diffs.clearReviewDiffs()
        process?.terminate()
        process = null
        s.controlClient.failAll("process gone")
    }

    private fun onTerminated(gen: Int, exitCode: Int) {
        if (gen != generation) return
        val staleResume = resumedLaunch && !s.catalog.initialized
        s.flushDeltas()
        s.controlClient.failAll("process gone")
        edt {
            s.turn.reset()
            ready = false
            s.catalog.initialized = false
            s.prompts.dropSuggestion()
            s.cardManager.clear()
            s.taskTracker.clear()
            s.hookNarrator.clear()
            if (exitCode != 0 && staleResume) {
                log.info("resume of session ${s.sessionId} failed (exit $exitCode) — continuing as a new conversation")
                s.sessionId = null
                resumedLaunch = false
                s.systemNotice("That conversation is no longer available — started a new one.")
                fireState()
                s.start(resume = false)
                return@edt
            }
            if (exitCode != 0) {
                s.transcript.add(Speaker.ERROR, "Claude Code exited (code $exitCode).")
                s.notifier.error("Claude Code exited unexpectedly (code $exitCode).")
                fireAttention(AttentionReason.ERROR)
            } else {
                s.systemNotice("Session ended.")
            }
            fireState()
        }
    }
}
