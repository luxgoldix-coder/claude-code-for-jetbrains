package dev.lain.claudejb.permission

import dev.lain.claudejb.permission.SensitiveGuard.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScannerPathSplitTest : GuardProbe(
    SensitiveGuard.Policy(
        globs = CredentialPaths.SENSITIVE_GLOBS,
        home = """C:\Users\me""",
        currentUser = "me",
        projectRoot = """C:\build""",
        caseInsensitivePaths = true,
    ),
) {

    @Test
    fun `a Windows PATH prepend is split on the semicolon, so the injected directory is judged`() {
        val cmd = """set "PATH=C:\evil;%PATH%" & git status"""
        assertEquals(Verdict.DENY, v(bash(cmd)))
        assertEquals(SecurityRule.OUTSIDE_PROJECT, rule(bash(cmd)))
    }

    @Test
    fun `the same shape with the directory inside the project is allowed`() {
        val cmd = """set "PATH=C:\build\bin;%PATH%" & git status"""
        assertEquals(Verdict.ALLOW, v(bash(cmd)))
    }

    @Test
    fun `a POSIX PATH prepend is still split on the colon`() {
        val posix = GuardFixture.basePolicy()
        val cmd = "PATH=/opt/evil:\$PATH git status"
        assertEquals(Verdict.DENY, v(bash(cmd), posix))
        assertEquals(SecurityRule.OUTSIDE_PROJECT, rule(bash(cmd), posix))
    }
}
