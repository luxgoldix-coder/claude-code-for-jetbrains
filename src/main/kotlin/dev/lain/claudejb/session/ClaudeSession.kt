package dev.lain.claudejb.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import dev.lain.claudejb.context.Attachment
import dev.lain.claudejb.diff.EditSnapshot
import dev.lain.claudejb.permission.PendingPermission
import dev.lain.claudejb.process.ClaudeBinaryLocator
import dev.lain.claudejb.process.ClaudeProcess
import dev.lain.claudejb.protocol.AccountInfo
import dev.lain.claudejb.protocol.AgentInfo
import dev.lain.claudejb.protocol.AuthStatusInfo
import dev.lain.claudejb.protocol.ClaudeEvent
import dev.lain.claudejb.protocol.ClaudeJson
import dev.lain.claudejb.protocol.ContextUsage
import dev.lain.claudejb.protocol.ControlProtocol
import dev.lain.claudejb.protocol.InitializeResponse
import dev.lain.claudejb.protocol.ModelInfo
import dev.lain.claudejb.protocol.RateLimitInfo
import dev.lain.claudejb.protocol.SlashCommand
import dev.lain.claudejb.protocol.TaskProgressInfo
import dev.lain.claudejb.settings.ClaudeSettings
import dev.lain.claudejb.settings.LaunchDefaults
import dev.lain.claudejb.settings.Provider
import dev.lain.claudejb.settings.RemoteMounts
import dev.lain.claudejb.settings.SecretStore
import dev.lain.claudejb.settings.guardSuspended
import dev.lain.claudejb.settings.resolveEnv
import dev.lain.claudejb.util.edt
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class ClaudeSession(
    private val project: Project,
    @Volatile var title: String,
    val gitIntegration: Boolean = false,
) : Disposable {

    private val log = thisLogger()

    internal val notifier = SessionNotifier(project)

    val transcript = TranscriptModel()

    internal val tokens = TokenAccountant()
    internal val taskTracker = TaskTracker()
    internal val reconciler = TranscriptReconciler(transcript)

    internal val diffs = DiffLifecycleManager(project)
    private val rollback = RollbackManager(project, diffs, reseedReadState = { p, m -> queries.seedReadState(p, m) })
    internal val controlClient = SessionControlClient(write = ::write)

    val settings = SessionLiveSettings(
        session = this,
        project = project,
        edt = ::edt,
        fireState = ::fireState,
        write = ::write,
    )

    val queries = SessionQueries(
        controlClient = controlClient,
        isRunning = ::isRunning,
        edt = ::edt,
        write = ::write,
        quota = QuotaWarnings(log, QuotaWarnings.Announce(inTranscript = ::systemNotice, asNotification = notifier::info)),
    )

    private val titling = SessionTitling(
        currentTitle = { title },
        setTitle = { title = it },
        fireTitleChanged = { edt { fireTitleChanged() } },
        requestGeneratedTitle = queries::requestGeneratedTitle,
    )

    private val notices = NoticeNarrator(
        log = log,
        systemNotice = ::systemNotice,
        addRow = { speaker, text, meta -> transcript.add(speaker, text, meta = meta) },
        notifyInfo = notifier::info,
        edt = ::edt,
    )

    internal val cardManager = PermissionCardManager(::firePermissions)

    val cards = SessionCards(
        session = this,
        edt = ::edt,
        write = ::write,
        firePermissions = ::firePermissions,
        fireAttention = ::fireAttention,
    )
    internal val hookNarrator = HookActivityNarrator(transcript)

    val login = LoginCoordinator(
        project,
        edt = ::edt,
        notifier = notifier,
        restartSession = { restart() },
    )

    @Volatile var sessionId: String? = null
        internal set

    @Volatile var launch: LaunchOptions = LaunchOptions()
        internal set

    @Volatile var outputStyle: String = "default"
        internal set

    val turn = TurnState()

    @Volatile var rateLimit: RateLimitInfo? = null
        internal set

    @Volatile var rateLimits: Map<String, RateLimitInfo> = emptyMap()
        internal set

    @Volatile var sessionState: String? = null
        internal set

    @Volatile var authStatus: AuthStatusInfo? = null
        internal set

    val subagentTasks: Map<String, TaskProgressInfo> get() = taskTracker.tasks

    val runningAgents = AgentRegistry(subagentsDir = { sessionId?.let { SessionStore.subagentsDir(it) } })

    val backgroundTaskRegistry = BackgroundTaskRegistry()

    internal val agentScanner: AgentScanner = AgentScanner(
        project = project,
        agents = runningAgents,
        tasks = backgroundTaskRegistry,
        sessionId = { sessionId },
        ownerOfTask = ::ownerAgentOfTask,
        ui = object : AgentScanner.Ui {
            override fun labelCards() {
                toolEvents.labelAgentCards()
                poll.ensureAgentRevivalPoll()
            }
            override fun onFresh(fresh: List<String>) = fireAgents(fresh)
            override fun onOutputGrew() = fireState()
            override fun edt(block: () -> Unit) = dev.lain.claudejb.util.edt(block)
        },
    )

    fun ownerAgentOfTask(taskId: String): String? {
        val fromLink = backgroundTaskRegistry.taskOf(taskId)?.ownerToolUseId
        val fromEdge = subagentTasks[taskId]?.toolUseId
        val tool = fromLink ?: fromEdge ?: return null
        return runningAgents.nodes.values.firstOrNull { it.meta.toolUseId == tool }?.agentId
    }

    val backgroundTasks: List<dev.lain.claudejb.protocol.BackgroundTaskInfo> get() = taskTracker.backgroundTasks

    val liveInputTokens get() = tokens.liveInputTokens
    val liveCacheCreationTokens get() = tokens.liveCacheCreationTokens
    val liveCacheReadTokens get() = tokens.liveCacheReadTokens
    val liveOutputTokens get() = tokens.liveOutputTokens

    val sessionInputTokens get() = tokens.sessionInputTokens
    val sessionCacheCreationTokens get() = tokens.sessionCacheCreationTokens
    val sessionCacheReadTokens get() = tokens.sessionCacheReadTokens
    val sessionOutputTokens get() = tokens.sessionOutputTokens

    fun totalTokens(): Int = tokens.totalTokens()

    @Volatile internal var ready = false

    private val stream = StreamBuffer()

    private fun flushDeltas() {
        val drained = stream.drain() ?: return
        val apply = {
            for ((isThinking, text) in drained.runs) {
                if (isThinking) reconciler.appendThinking(text) else reconciler.appendAssistant(text)
            }
            drained.usage?.let { tokens.onLiveUsage(it[0], it[1], it[2], it[3]) }
        }
        if (ApplicationManager.getApplication().isDispatchThread) apply() else edt { apply() }
    }

    @Volatile internal var cachedEnv: Map<String, String>? = null

    var commands: List<SlashCommand> = emptyList()
        internal set
    var models: List<ModelInfo> = emptyList()
        private set
    var agents: List<AgentInfo> = emptyList()
        private set
    var availableOutputStyles: List<String> = emptyList()
        private set
    var account: AccountInfo = AccountInfo()
        private set
    val remote = RemoteControl(queries, transcript, ::fireState)

    @Volatile private var process: ClaudeProcess? = null

    @Volatile private var generation = 0

    @Volatile private var starting = false

    val prompts = PromptQueue(
        transcript = transcript,
        edt = ::edt,
        write = ::write,
        canSend = { ready && isRunning() && !turn.active },
        onSent = {
            turn.active = true
            poll.startQuotaPolling()
            poll.ensureAgentRevivalPoll()
        },
        fireState = ::fireState,
    )

    private val turnControl = TurnControl(this, ::edt, ::write, ::fireState)

    private val listeners = CopyOnWriteArrayList<SessionListener>()

    @Volatile var lastSessionCost: JsonObject? = null
        private set

    @Volatile var lastContextUsage: ContextUsage? = null
        private set

    val workingDir: String? get() = project.basePath

    @Volatile var binaryVersion: String? = null

    val checkpointingEnabled: Boolean get() = ClaudeSettings.getInstance(project).enableFileCheckpointing

    val guardEnforced: Boolean get() = !ClaudeSettings.getInstance(project).guardSuspended()

    internal val poll = PollSchedule(
        isRunning = ::isRunning,
        turnActive = { turn.active },
        effects = PollSchedule.SessionEffects(edt = ::edt, fireState = ::fireState),
        quota = PollSchedule.QuotaSource(
            requestSessionCost = queries::requestSessionCost,
            requestContextUsage = queries::requestContextUsage,
            onSessionCost = { lastSessionCost = it },
            onContextUsage = { lastContextUsage = it },
        ),
        outputTail = PollSchedule.OutputTailSource(
            anyTailable = { backgroundTaskRegistry.anyTailable },
            tailNow = { agentScanner.tailNow() },
        ),
        agentRevival = PollSchedule.AgentRevivalSource(
            anySettledAgent = { runningAgents.nodes.values.any { it.status != AgentStatus.RUNNING } },
            anyRunningAgent = { runningAgents.nodes.values.any { it.status == AgentStatus.RUNNING } },
            scanAgents = { agentScanner.scan() },
        ),
    )

    @Volatile
    var initialized: Boolean = false
        private set

    val guard = SessionGuard(
        session = this,
        project = project,
        edt = ::edt,
        write = ::write,
        fireState = ::fireState,
        fireAttention = ::fireAttention,
    )

    internal val toolEvents = ToolEvents(this, ::edt, ::fireState)
    private val taskEvents = TaskEvents(this, ::edt, ::fireState)
    private val signalEvents = SignalEvents(this, ::edt, ::fireState, ::fireMetadata)
    private val controlEvents = ControlEvents(this, ::edt)
    internal val conversation = ConversationEvents(this, project, ::edt, ::fireState, ::fireAttention)

    fun addListener(listener: SessionListener) {
        listeners.add(listener)
        edt { poll.pollQuota() }
    }

    fun removeListener(listener: SessionListener) {
        listeners.remove(listener)
        edt { if (listeners.isEmpty() && poll.quotaRunning) poll.stopQuota() }
    }

    fun isRunning(): Boolean = process?.isRunning() == true

    fun isStarting(): Boolean = starting

    fun start(resume: Boolean = sessionId != null): Boolean {
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
        initialized = false
        starting = true
        reconciler.onMessageBoundary()
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

    @Volatile
    var binaryMissing: Boolean = false
        private set

    @Volatile
    var needsLogin: Boolean = false
        internal set

    internal fun onLoginNeeded() {
        needsLogin = true
        edt { fireState() }
        login.maybePrompt()
    }

    val auth = AuthGate(
        project = project,
        signInInProgress = { login.inProgress },
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

    @Volatile
    private var resumedLaunch = false

    fun refreshBootState() {
        if (starting) return
        if (login.inProgress) return
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

                !isRunning() -> start()
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
            notifier.missingBinary()
            return null
        }
        binaryMissing = false
        if (settings.claudePath != binary.absolutePath) {
            settings.update { it.claudePath = binary.absolutePath }
        }
        return binary
    }

    private fun passesLaunchGates(settings: ClaudeSettings): Boolean {
        if (!notifier.ensureExecTrust(settings)) return false
        if (RemoteMounts.isRemote(project.basePath)) {
            refuseRemoteProject(project.basePath)
            return false
        }
        return true
    }

    internal fun effectiveLaunchEnv(base: Map<String, String>? = null): Map<String, String> {
        val env = base ?: ClaudeSettings.getInstance(project).resolveEnv()
        val settings = ClaudeSettings.getInstance(project)
        val apiKey = settings.anthropicApiKey
            .takeIf { it.isNotBlank() && settings.provider == Provider.ANTHROPIC && SecretStore.API_KEY !in env }
        val withSecrets = env +
            SecretStore.envOverlay(env.keys) +
            (apiKey?.let { mapOf(SecretStore.API_KEY to it) } ?: emptyMap())
        return withSecrets + dev.lain.claudejb.process.CredentialsVault.envOverlay(withSecrets.keys)
    }

    private fun launch(launchGen: Int, settings: ClaudeSettings, binary: File, workDir: File, resume: Boolean) {
        if (!auth.renew(binary, settings)) {
            edt { onLoginNeeded() }
            return
        }
        val env = effectiveLaunchEnv(cachedEnv ?: settings.resolveEnv().also { cachedEnv = it })
        if (launchGen != generation) return
        resumedLaunch = resume
        val opts = launchOptions()
        val proc = ClaudeProcess(
            binary = binary,
            workDir = workDir,
            args = SessionLauncher.buildArgs(opts, resume, SessionLauncher.mcpConfigJson(opts)),
            nodeOverride = settings.nodePath,
            extraEnv = env,
            onEvent = ::onEvent,
            onTerminated = { code -> onTerminated(launchGen, code) },
        )
        process = proc
        val started = runCatching { proc.start() }
        if (started.isFailure) {
            process = null
            log.warn("Failed to start the claude process", started.exceptionOrNull())
            notifier.error("Failed to start Claude Code: ${started.exceptionOrNull()?.message ?: "unknown error"}")
            return
        }
        if (launchGen != generation) {
            proc.terminate()
            if (process === proc) process = null
            return
        }
        requestInitialize()
        edt {
            ready = true
            transcript.add(Speaker.SYSTEM, "Claude Code ready.")
            auth.probe()
            fireState()
            poll.pollQuota()
            pump()
        }
    }

    private fun requestInitialize() {
        controlClient.query(
            buildRequest = ControlProtocol::initializeRequest,
            decode = { payload ->
                payload?.let {
                    runCatching { ClaudeJson.decodeFromJsonElement(InitializeResponse.serializer(), it) }
                        .onFailure { e -> log.debug("Failed to decode initialize response", e) }
                        .getOrNull()
                }
            },
            onResult = { info: InitializeResponse? ->
                info ?: return@query
                commands = info.commands
                models = info.models
                agents = info.agents
                availableOutputStyles = info.availableOutputStyles
                account = info.account
                log.debug(
                    "CC-TRACE initialize reply: account(email=${info.account.email.isNotBlank()}," +
                        " org=${info.account.organization.isNotBlank()}, plan='${info.account.subscriptionType}'," +
                        " provider='${info.account.apiProvider}') models=${info.models.size}" +
                        " commands=${info.commands.size} agents=${info.agents.size}",
                )
                initialized = true
                if (info.outputStyle.isNotBlank()) outputStyle = info.outputStyle
                val pinMissing = info.models.isNotEmpty() && info.models.none { it.value == LaunchDefaults.DEFAULT_MODEL }
                if (launch.model == LaunchDefaults.DEFAULT_MODEL && pinMissing) {
                    settings.changeModel(LaunchDefaults.preferredDefault(info.models), persist = false)
                }
                edt { fireMetadata() }
            },
        )
    }

    private fun launchOptions() = launch.copy(sessionId = sessionId)

    fun restart(resume: Boolean = true) {
        stop()
        start(resume)
    }

    fun stop() {
        generation++
        flushDeltas()
        turnControl.cancelPendingElicitations()
        process?.terminate()
        process = null
        turn.reset()
        ready = false
        initialized = false
        starting = false
        prompts.dropSuggestion()
        cachedEnv = null
        controlClient.failAll("process gone")
        taskTracker.clear()
        hookNarrator.clear()
        backgroundTaskRegistry.clear()
        agentScanner.clearTails()
        edt {
            cardManager.clear()
            diffs.clearReviewDiffs()
            fireState()
        }
    }

    fun send(text: String) = send(text, emptyList())

    fun send(text: String, attachments: List<Attachment>) {
        val composed = PromptComposer.compose(text, attachments, project.basePath) ?: return
        if (!isRunning()) {
            if (!start()) return
        }
        prompts.enqueue(composed.wireText, composed.images, composed.displayText)
    }

    fun sendSideQuestion(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (!isRunning()) {
            if (!start()) return
            prompts.enqueue(trimmed, emptyList(), trimmed)
            return
        }
        edt {
            transcript.add(Speaker.USER, "↪ $trimmed")
            queries.askSideQuestion(trimmed) { answer ->
                transcript.add(Speaker.SYSTEM, answer?.let { "↩ $it" } ?: SIDE_QUESTION_UNANSWERED)
            }
            prompts.pump()
        }
    }

    fun interrupt() = turnControl.interrupt()

    fun editSnapshot(toolUseId: String): EditSnapshot? = diffs.snapshot(toolUseId)

    fun restore(savedSessionId: String, dtos: List<EntryDTO>, fork: Boolean = false) {
        sessionId = savedSessionId
        launch = launch.copy(fork = fork)
        agentScanner.restoreAdmitted(onTasksReplayed = ::fireState)
        prompts.forgetTurn()
        val saved = guard.restore(savedSessionId)
        val withGuard = GuardRestore.reinstate(dtos, GuardRestore.raisedInThisChat(dtos, saved))
        edt {
            transcript.clear()
            for (dto in withGuard) {
                val speaker = runCatching { Speaker.valueOf(dto.speaker) }.getOrNull() ?: continue
                transcript.add(
                    speaker,
                    dto.text,
                    meta = dto.meta,
                    toolUseId = dto.toolUseId,
                    parentToolUseId = dto.parentToolUseId,
                    filePath = dto.filePath,
                    commandText = dto.commandText,
                    messageText = dto.messageText,
                    blockedRule = dto.blockedRule,
                    bypassedRule = dto.bypassedRule,
                    bypassAction = dto.bypassAction,
                    toolState = when {
                        dto.failed -> ToolState.ERROR
                        dto.inFlight -> ToolState.ERROR
                        dto.meta == "Task" || dto.meta == "Agent" -> ToolState.ERROR
                        else -> ToolState.FINISHED
                    },
                )
            }
        }
    }

    internal fun recordOpenAndTitle(id: String) {
        AppExecutorUtil.getAppExecutorService().execute {
            if (!gitIntegration) titling.resolve(id)
            SessionHistory.getInstance(project).setOpenSessions(
                ChatSessionManager.getInstance(project).all()
                    .filterNot { it.gitIntegration }
                    .mapNotNull { it.sessionId },
            )
        }
    }

    fun pendingPermissions(): List<PendingPermission> = cards.pending()

    fun resolvePermission(
        requestId: String,
        allow: Boolean,
        denyMessage: String? = null,
        overrideInput: JsonObject? = null,
    ) = cards.resolvePermission(requestId, allow, denyMessage, overrideInput)

    val provider: Provider get() = ClaudeSettings.getInstance(project).provider

    fun refreshAfterRewind(paths: List<String>) {
        paths.forEach { diffs.markForRefresh(it) }
        diffs.refreshTouched()
    }

    fun revertEdit(snapshot: EditSnapshot): Boolean {
        val name = java.io.File(snapshot.filePath).name
        val ok = rollback.revertEdit(snapshot)
        if (ok) {
            notifier.info("Reverted $name to its state before this edit.")
        } else {
            notifier.error("Couldn't revert $name (the file may be outside the project, missing, or locked).")
        }
        return ok
    }

    fun renameSession(title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        if (isRunning()) write(ControlProtocol.renameSessionRequest(ControlProtocol.newRequestId(), trimmed))
        titling.markRenamed()
        this.title = trimmed
        edt { fireTitleChanged() }
    }

    @org.jetbrains.annotations.TestOnly
    fun handleEventForTest(event: ClaudeEvent) {
        onEvent(event)
        flushDeltas()
    }

    private fun onEvent(event: ClaudeEvent) {
        if (event is ClaudeEvent.Stream) {
            stream.buffer(event)
            return
        }
        flushDeltas()
        when (event) {
            is ClaudeEvent.Conversation -> conversation.onConversation(event)
            is ClaudeEvent.Control -> controlEvents.onControl(event)
            is ClaudeEvent.Task -> taskEvents.onTask(event)
            is ClaudeEvent.SessionSignal -> signalEvents.onSessionSignal(event)
            is ClaudeEvent.HookTelemetry -> controlEvents.onHookTelemetry(event)
            is ClaudeEvent.Notice -> notices.onNotice(event)
            is ClaudeEvent.Stream -> {}
        }
    }

    private fun onTerminated(gen: Int, exitCode: Int) {
        if (gen != generation) return
        val staleResume = resumedLaunch && !initialized
        flushDeltas()
        controlClient.failAll("process gone")
        edt {
            turn.reset()
            ready = false
            initialized = false
            prompts.dropSuggestion()
            cardManager.clear()
            taskTracker.clear()
            hookNarrator.clear()
            if (exitCode != 0 && staleResume) {
                log.info("resume of session $sessionId failed (exit $exitCode) — continuing as a new conversation")
                sessionId = null
                resumedLaunch = false
                systemNotice("That conversation is no longer available — started a new one.")
                fireState()
                start(resume = false)
                return@edt
            }
            if (exitCode != 0) {
                transcript.add(Speaker.ERROR, "Claude Code exited (code $exitCode).")
                notifier.error("Claude Code exited unexpectedly (code $exitCode).")
                fireAttention(AttentionReason.ERROR)
            } else {
                systemNotice("Session ended.")
            }
            fireState()
        }
    }

    internal fun write(line: String): Boolean = process?.writeLine(line) ?: false

    internal fun systemNotice(message: String) = edt { transcript.add(Speaker.SYSTEM, message) }

    fun scanAgents() = agentScanner.scan()

    private fun fireAgents(fresh: List<String>) = listeners.forEach { it.onAgentsChanged(fresh) }

    private fun fireState() = listeners.forEach { it.onStateChanged() }
    private fun fireMetadata() = listeners.forEach { it.onMetadataChanged() }
    private fun firePermissions() = listeners.forEach { it.onPermissionsChanged() }
    private fun fireAttention(reason: AttentionReason, landing: AttentionLanding = AttentionLanding.Chat) =
        listeners.forEach { it.onAttention(reason, landing) }
    private fun fireTitleChanged() = listeners.forEach { it.onTitleChanged() }

    private fun refuseRemoteProject(root: String?) {
        val msg = SessionNotifier.remoteProjectRefusal(root)
        edt {
            transcript.add(Speaker.ERROR, msg)
            fireState()
        }
        notifier.error(msg)
        starting = false
    }

    override fun dispose() {
        generation++
        starting = false
        poll.stopAll()
        turnControl.cancelPendingElicitations()
        diffs.clearReviewDiffs()
        process?.terminate()
        process = null
        controlClient.failAll("process gone")
    }

    fun modelOptions(): List<ModelInfo> = models

    fun preferredDefaultModel(): String = LaunchDefaults.preferredDefault(models)

    companion object {
        const val EXPIRED_TOKEN_NOTICE =
            "Your access token expired while this chat was open. The sign-in itself is still valid and is " +
                "renewed when a session starts, but a running one cannot pick up the new token — so this turn " +
                "did not complete, and sending it again will fail the same way. Close this chat and open it " +
                "again to continue."

        const val SIDE_QUESTION_UNANSWERED = "↩ The side question was not answered."

        const val CONTROL_TIMEOUT_SECONDS = 30L
    }
}
