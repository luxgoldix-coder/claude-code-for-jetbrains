package dev.lain.claudejb.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import dev.lain.claudejb.context.Attachment
import dev.lain.claudejb.diff.EditSnapshot
import dev.lain.claudejb.permission.PendingPermission
import dev.lain.claudejb.protocol.ClaudeEvent
import dev.lain.claudejb.protocol.ControlProtocol
import dev.lain.claudejb.protocol.TaskProgressInfo
import dev.lain.claudejb.settings.ClaudeSettings
import dev.lain.claudejb.settings.Provider
import dev.lain.claudejb.settings.guardSuspended
import dev.lain.claudejb.util.edt
import kotlinx.serialization.json.JsonObject
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
        restartSession = { if (!lifecycle.disposed) restart() },
    )

    @Volatile var sessionId: String? = null
        internal set

    @Volatile var launch: LaunchOptions = LaunchOptions()
        internal set

    val turn = TurnState()

    val signals = SessionSignals()

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

    private val stream = StreamBuffer()

    internal fun flushDeltas() {
        val drained = stream.drain() ?: return
        val apply = {
            for ((isThinking, text) in drained.runs) {
                if (isThinking) reconciler.appendThinking(text) else reconciler.appendAssistant(text)
            }
            drained.usage?.let { tokens.onLiveUsage(it[0], it[1], it[2], it[3]) }
        }
        if (ApplicationManager.getApplication().isDispatchThread) apply() else edt { apply() }
    }

    val catalog = BinaryCatalog(this, ::fireMetadata)

    val remote = RemoteControl(queries, transcript, ::fireState)

    val prompts = PromptQueue(
        transcript = transcript,
        edt = ::edt,
        write = ::write,
        canSend = { lifecycle.ready && isRunning() && !turn.active },
        onSent = {
            turn.active = true
            poll.startQuotaPolling()
            poll.ensureAgentRevivalPoll()
        },
        fireState = ::fireState,
    )

    internal val turnControl = TurnControl(this, ::edt, ::write, ::fireState)

    private val listeners = CopyOnWriteArrayList<SessionListener>()

    val workingDir: String? get() = project.basePath

    val checkpointingEnabled: Boolean get() = ClaudeSettings.getInstance(project).enableFileCheckpointing

    val guardEnforced: Boolean get() = !ClaudeSettings.getInstance(project).guardSuspended()

    internal val poll = PollSchedule(
        isRunning = ::isRunning,
        turnActive = { turn.active },
        effects = PollSchedule.SessionEffects(edt = ::edt, fireState = ::fireState),
        quota = PollSchedule.QuotaSource(
            requestSessionCost = queries::requestSessionCost,
            requestContextUsage = queries::requestContextUsage,
            onSessionCost = { signals.lastSessionCost = it },
            onContextUsage = { signals.lastContextUsage = it },
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

    val lifecycle = SessionLifecycle(this, project, ::edt, ::fireState, ::fireAttention, ::onEvent)

    fun addListener(listener: SessionListener) {
        listeners.add(listener)
        edt { poll.pollQuota() }
    }

    fun removeListener(listener: SessionListener) {
        listeners.remove(listener)
        edt { if (listeners.isEmpty() && poll.quotaRunning) poll.stopQuota() }
    }

    fun isRunning(): Boolean = lifecycle.isRunning()

    fun isStarting(): Boolean = lifecycle.isStarting()

    fun start(resume: Boolean = sessionId != null): Boolean = lifecycle.start(resume)

    fun restart(resume: Boolean = true) = lifecycle.restart(resume)

    fun stop() = lifecycle.stop()

    fun refreshBootState() = lifecycle.refreshBootState()

    fun dismissLoginCard() = lifecycle.dismissLoginCard()

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

    internal fun write(line: String): Boolean = lifecycle.write(line)

    internal fun systemNotice(message: String) = edt { transcript.add(Speaker.SYSTEM, message) }

    fun scanAgents() = agentScanner.scan()

    private fun fireAgents(fresh: List<String>) = listeners.forEach { it.onAgentsChanged(fresh) }

    private fun fireState() = listeners.forEach { it.onStateChanged() }
    private fun fireMetadata() = listeners.forEach { it.onMetadataChanged() }
    private fun firePermissions() = listeners.forEach { it.onPermissionsChanged() }
    private fun fireAttention(reason: AttentionReason, landing: AttentionLanding = AttentionLanding.Chat) =
        listeners.forEach { it.onAttention(reason, landing) }
    private fun fireTitleChanged() = listeners.forEach { it.onTitleChanged() }

    override fun dispose() = lifecycle.shutdown()

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
