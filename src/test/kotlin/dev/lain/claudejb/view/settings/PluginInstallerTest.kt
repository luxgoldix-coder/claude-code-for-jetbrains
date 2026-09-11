package dev.lain.claudejb.view.settings

import com.intellij.openapi.project.Project
import dev.lain.claudejb.model.session.launch.IdeServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PluginInstallerTest {

    private class FakePresence(private val enabled: Set<String>) : PluginPresence {
        val installed = mutableListOf<String>()

        override fun isEnabled(pluginId: String) = pluginId in enabled

        override fun install(project: Project?, pluginId: String, onSuccess: () -> Unit) {
            installed += pluginId
            onSuccess()
        }
    }

    private val project: Project? = null

    @Test
    fun `an installed plugin is taken as is, without asking`() {
        val presence = FakePresence(setOf(IdeServer.INDEX.pluginId))
        var asked = false
        val installer = PluginInstaller(project, presence) { _, _ ->
            asked = true
            true
        }
        var outcome: Boolean? = null
        installer.ensureInstalled(IdeServer.INDEX) { outcome = it }
        assertEquals(true, outcome)
        assertFalse(asked)
        assertTrue(presence.installed.isEmpty())
    }

    @Test
    fun `a missing plugin is offered, and a yes installs it through the IDE`() {
        val presence = FakePresence(emptySet())
        var shown = ""
        val installer = PluginInstaller(project, presence) { _, message ->
            shown = message
            true
        }
        var outcome: Boolean? = null
        installer.ensureInstalled(IdeServer.DEBUGGER) { outcome = it }
        assertEquals(true, outcome)
        assertEquals(listOf(IdeServer.DEBUGGER.pluginId), presence.installed)
        assertTrue(shown.contains("third-party plugin by hechtcarmel"), shown)
    }

    @Test
    fun `a no leaves the plugin alone and reports it`() {
        val presence = FakePresence(emptySet())
        val installer = PluginInstaller(project, presence) { _, _ -> false }
        var outcome: Boolean? = null
        installer.ensureInstalled(IdeServer.INDEX) { outcome = it }
        assertEquals(false, outcome)
        assertTrue(presence.installed.isEmpty())
    }

    @Test
    fun `the JetBrains server carries no third-party disclaimer`() {
        assertFalse(PluginInstaller.message(IdeServer.JETBRAINS).contains("third-party"))
        assertTrue(PluginInstaller.message(IdeServer.INDEX).contains("localhost port"))
    }
}
