package dev.lain.claudejb.permission

import dev.lain.claudejb.permission.SensitiveGuard.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnvIndirectionWindowsTest : GuardProbe(
    SensitiveGuard.Policy(
        globs = CredentialPaths.SENSITIVE_GLOBS,
        home = """C:\Users\me""",
        currentUser = "me",
        projectRoot = """C:\build""",
        caseInsensitivePaths = true,
    ),
) {

    @Test
    fun `cmd set binds its variable, so a later reference is not an unresolved destination`() {
        assertEquals(Verdict.ALLOW, v(bash("""set "X=C:/build" & copy a %X%""")))
    }

    @Test
    fun `a variable nothing here can resolve still cards`() {
        assertEquals(Verdict.DENY, v(bash("""cat %NOWHERE%\x""")))
        assertEquals(SecurityRule.UNRESOLVED_VARIABLE, rule(bash("""cat %NOWHERE%\x""")))
        assertEquals(Verdict.DENY, v(bash("cat \$CREDS")))
        assertEquals(SecurityRule.UNRESOLVED_VARIABLE, rule(bash("cat \$CREDS")))
    }
}
