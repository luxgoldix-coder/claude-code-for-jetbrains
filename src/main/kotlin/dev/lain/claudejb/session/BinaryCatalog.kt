package dev.lain.claudejb.session

import com.intellij.openapi.diagnostic.thisLogger
import dev.lain.claudejb.protocol.AccountInfo
import dev.lain.claudejb.protocol.AgentInfo
import dev.lain.claudejb.protocol.InitializeResponse
import dev.lain.claudejb.protocol.ModelInfo
import dev.lain.claudejb.protocol.SlashCommand
import dev.lain.claudejb.settings.LaunchDefaults

class BinaryCatalog(
    private val s: ClaudeSession,
    private val fireMetadata: () -> Unit,
) {

    private val log = thisLogger()

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

    @Volatile var outputStyle: String = "default"
        internal set

    @Volatile var initialized: Boolean = false
        internal set

    @Volatile var binaryVersion: String? = null

    fun preferredDefaultModel(): String = LaunchDefaults.preferredDefault(models)

    fun request() = s.queries.ask(Asks.INITIALIZE) { info -> info?.let(::adopt) }

    private fun adopt(info: InitializeResponse) {
        commands = info.commands
        models = info.models
        agents = info.agents
        availableOutputStyles = info.availableOutputStyles
        account = info.account
        log.debug(
            "initialize reply: account(email=${info.account.email.isNotBlank()}," +
                " org=${info.account.organization.isNotBlank()}, plan='${info.account.subscriptionType}'," +
                " provider='${info.account.apiProvider}') models=${info.models.size}" +
                " commands=${info.commands.size} agents=${info.agents.size}",
        )
        initialized = true
        if (info.outputStyle.isNotBlank()) outputStyle = info.outputStyle
        val pinMissing = info.models.isNotEmpty() && info.models.none { it.value == LaunchDefaults.DEFAULT_MODEL }
        if (s.launch.model == LaunchDefaults.DEFAULT_MODEL && pinMissing) {
            s.settings.changeModel(LaunchDefaults.preferredDefault(info.models), persist = false)
        }
        fireMetadata()
    }
}
