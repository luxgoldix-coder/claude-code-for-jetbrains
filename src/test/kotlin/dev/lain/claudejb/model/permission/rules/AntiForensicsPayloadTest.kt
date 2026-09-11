package dev.lain.claudejb.permission

import dev.lain.claudejb.permission.SensitiveGuard.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class AntiForensicsPayloadTest : GuardProbe() {

    @Test
    fun `a timestamp assignment inside an Edit payload is content, not a command`() {
        val input = edit("/home/me/proj/tools/Stamp.ps1", "old", "\$f.CreationTime = Get-Date")
        assertNotEquals(SecurityRule.ANTI_FORENSIC, rule(input))
        assertEquals(Verdict.ALLOW, v(input))
    }

    @Test
    fun `the same assignment run as a command is timestomping`() {
        val cmd = bash("\$f.CreationTime = '2020-01-01'")
        assertEquals(Verdict.DENY, v(cmd))
        assertEquals(SecurityRule.ANTI_FORENSIC, rule(cmd))
    }
}
