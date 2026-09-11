package dev.lain.claudejb.view.settings

import com.intellij.openapi.project.Project
import dev.lain.claudejb.model.session.launch.IdeServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PluginInstallerTest {

    private class FakePresence(
        private val states: MutableMap<String, PluginState>,
        private val afterInstall: PluginState = PluginState.PENDING_RESTART,
    ) : PluginPresence {
        val installed = mutableListOf<String>()
        var restarts = 0

        override fun state(pluginId: String) = states[pluginId] ?: PluginState.MISSING

        override fun install(project: Project?, pluginId: String, onSuccess: () -> Unit) {
            installed += pluginId
            states[pluginId] = afterInstall
            onSuccess()
        }

        override fun restart() {
            restarts++
        }
    }

    private val project: Project? = null

    private fun installer(presence: PluginPresence, answer: (title: String, message: String, yes: String) -> Boolean) =
        PluginInstaller(project, presence, answer)

    @Test
    fun `an installed plugin is taken as is, without asking`() {
        val presence = FakePresence(mutableMapOf(IdeServer.INDEX.pluginId to PluginState.READY))
        var asked = false
        val installer = installer(presence) { _, _, _ ->
            asked = true
            true
        }
        var outcome: PluginState? = null
        installer.install(IdeServer.INDEX) { outcome = it }
        assertEquals(PluginState.READY, outcome)
        assertFalse(asked)
        assertTrue(presence.installed.isEmpty())
    }

    @Test
    fun `a missing plugin is offered, a yes installs it through the IDE, and it then waits for a restart`() {
        val presence = FakePresence(mutableMapOf())
        var shown = ""
        var button = ""
        val installer = installer(presence) { _, message, yes ->
            shown = message
            button = yes
            true
        }
        var outcome: PluginState? = null
        installer.install(IdeServer.DEBUGGER) { outcome = it }
        assertEquals(PluginState.PENDING_RESTART, outcome)
        assertEquals(listOf(IdeServer.DEBUGGER.pluginId), presence.installed)
        assertEquals("Install", button)
        assertTrue(shown.contains("third-party plugin by hechtcarmel"), shown)
        assertEquals(PluginState.PENDING_RESTART, installer.state(IdeServer.DEBUGGER))
    }

    @Test
    fun `a plugin the IDE loads without restarting is ready as soon as it is installed`() {
        val presence = FakePresence(mutableMapOf(), afterInstall = PluginState.READY)
        val installer = installer(presence) { _, _, _ -> true }
        var outcome: PluginState? = null
        installer.install(IdeServer.INDEX) { outcome = it }
        assertEquals(PluginState.READY, outcome)
    }

    @Test
    fun `a no leaves the plugin alone and reports it missing`() {
        val presence = FakePresence(mutableMapOf())
        val installer = installer(presence) { _, _, _ -> false }
        var outcome: PluginState? = null
        installer.install(IdeServer.INDEX) { outcome = it }
        assertEquals(PluginState.MISSING, outcome)
        assertTrue(presence.installed.isEmpty())
    }

    @Test
    fun `a plugin pending a restart is not installed twice`() {
        val presence = FakePresence(mutableMapOf(IdeServer.INDEX.pluginId to PluginState.PENDING_RESTART))
        var asked = false
        val installer = installer(presence) { _, _, _ ->
            asked = true
            true
        }
        var outcome: PluginState? = null
        installer.install(IdeServer.INDEX) { outcome = it }
        assertEquals(PluginState.PENDING_RESTART, outcome)
        assertFalse(asked)
        assertTrue(presence.installed.isEmpty())
    }

    @Test
    fun `restarting asks first, names the plugin, and only a yes restarts the IDE`() {
        val presence = FakePresence(mutableMapOf(IdeServer.INDEX.pluginId to PluginState.PENDING_RESTART))
        var shown = ""
        var button = ""
        var answer = false
        val installer = installer(presence) { _, message, yes ->
            shown = message
            button = yes
            answer
        }

        installer.restart(IdeServer.INDEX)
        assertEquals(0, presence.restarts)
        assertEquals("Restart", button)
        assertTrue(shown.contains(IdeServer.INDEX.label), shown)

        answer = true
        installer.restart(IdeServer.INDEX)
        assertEquals(1, presence.restarts)
    }

    @Test
    fun `the JetBrains server carries no third-party disclaimer`() {
        assertFalse(PluginInstaller.installMessage(IdeServer.JETBRAINS).contains("third-party"))
        assertTrue(PluginInstaller.installMessage(IdeServer.INDEX).contains("localhost port"))
    }
}
