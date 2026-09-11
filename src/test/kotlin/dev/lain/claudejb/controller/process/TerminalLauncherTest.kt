package dev.lain.claudejb.controller.process

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TerminalLauncherTest {

    @Test
    fun `posix command uses the quoted absolute path and the auth login subcommand`() {
        assertEquals(
            "\"/home/u/.local/bin/claude\" auth login",
            TerminalLauncher.loginCommand("/home/u/.local/bin/claude", isWindows = false),
        )
    }

    @Test
    fun `posix path with spaces stays quoted as a single token and is not backgrounded`() {
        val cmd = TerminalLauncher.loginCommand("/Applications/My Tools/claude", isWindows = false)
        assertEquals("\"/Applications/My Tools/claude\" auth login", cmd)
        assertFalse(cmd.startsWith("&"))
    }

    @Test
    fun `windows command is prefixed with the PowerShell call operator`() {
        assertEquals(
            "& \"C:\\Users\\u\\scoop\\shims\\claude.exe\" auth login",
            TerminalLauncher.loginCommand("C:\\Users\\u\\scoop\\shims\\claude.exe", isWindows = true),
        )
    }

    @Test
    fun `windows path with spaces stays a single quoted token after the call operator`() {
        val cmd = TerminalLauncher.loginCommand("C:\\Program Files\\claude\\claude.exe", isWindows = true)
        assertEquals("& \"C:\\Program Files\\claude\\claude.exe\" auth login", cmd)
        assertTrue(cmd.startsWith("& \""))
    }

    @Test
    fun `the subcommand comes from the caller, so Console and SSO are not silently turned into a plain login`() {
        assertEquals(
            "\"/usr/bin/claude\" auth login --console",
            TerminalLauncher.loginCommand("/usr/bin/claude", listOf("auth", "login", "--console"), isWindows = false),
        )
        assertEquals(
            "& \"C:\\bin\\claude.exe\" auth login --sso",
            TerminalLauncher.loginCommand("C:\\bin\\claude.exe", listOf("auth", "login", "--sso"), isWindows = true),
        )
    }
}

class TerminalApiContractTest {

    @Test
    fun `the public tab creation API the launcher calls exists on this platform`() {
        val cls = Class.forName("org.jetbrains.plugins.terminal.TerminalToolWindowManager")
        val m = cls.getMethod(
            "createShellWidget",
            String::class.java,
            String::class.java,
            java.lang.Boolean.TYPE,
            java.lang.Boolean.TYPE,
        )
        assertEquals(
            "com.intellij.terminal.ui.TerminalWidget",
            m.returnType.name,
            "createShellWidget must return the widget the launcher sends the command to",
        )
        assertTrue(
            m.returnType.methods.any { it.name == "sendCommandToExecute" && it.parameterCount == 1 },
            "TerminalWidget.sendCommandToExecute is how the command reaches the shell",
        )
    }

    @Test
    fun `the launcher reaches the terminal without reflection`() {
        val source = sequenceOf(
            java.io.File("src/main/kotlin/dev/lain/claudejb/controller/process/TerminalLauncher.kt"),
            java.io.File("../src/main/kotlin/dev/lain/claudejb/controller/process/TerminalLauncher.kt"),
        ).first { it.isFile }.readText()

        assertEquals(0, Regex("""\bgetMethod\(""").findAll(source).count()) {
            "TerminalLauncher went back to reflection; the five-argument createNewSession is @ApiStatus.Internal"
        }
        assertTrue(
            source.contains("createShellWidget("),
            "TerminalLauncher must call the public createShellWidget",
        )
    }
}
