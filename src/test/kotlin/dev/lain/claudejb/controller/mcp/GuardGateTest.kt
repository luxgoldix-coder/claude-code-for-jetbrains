package dev.lain.claudejb.controller.mcp

import dev.lain.claudejb.model.mcp.ToolSpec
import dev.lain.claudejb.model.permission.SensitiveGuard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GuardGateTest {

    private val spec = ToolSpec("read_file", "reads")
    private val realGuard = GuardGate { SensitiveGuard.evaluate(it, SensitiveGuard.Policy()) }

    @Test
    fun `a destructive command nested inside the run arguments is refused by the real guard`() {
        val arguments = Json.parseToJsonElement("""{"tool":"shell","args":{"command":"rm -rf /"}}""").jsonObject
        assertNotNull(realGuard.denial(spec, arguments))
    }

    @Test
    fun `a plain project path passes the real guard`() {
        val arguments = Json.parseToJsonElement("""{"tool":"read_file","args":{"path":"src/main/kotlin/App.kt"}}""").jsonObject
        assertNull(realGuard.denial(spec, arguments))
    }

    @Test
    fun `ask is a refusal too, since nobody sits at the socket to answer`() {
        val gate = GuardGate { SensitiveGuard.Decision(SensitiveGuard.Verdict.ASK, "needs a human") }
        assertEquals("needs a human", gate.denial(spec, Json.parseToJsonElement("{}").jsonObject))
    }

    @Test
    fun `a denial without a reason still names the guard`() {
        val gate = GuardGate { SensitiveGuard.Decision(SensitiveGuard.Verdict.DENY, null) }
        assertEquals("refused by the Sensitive Guard", gate.denial(spec, Json.parseToJsonElement("{}").jsonObject))
    }
}
