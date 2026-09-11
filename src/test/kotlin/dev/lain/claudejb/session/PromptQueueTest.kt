package dev.lain.claudejb.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PromptQueueTest {

    private val transcript = TranscriptModel()
    private val written = mutableListOf<String>()
    private var turnActive = false
    private var writeSucceeds = true
    private var stateFired = 0

    private val queue = PromptQueue(
        transcript = transcript,
        edt = { it() },
        write = { line ->
            written.add(line)
            writeSucceeds
        },
        canSend = { !turnActive },
        onSent = { turnActive = true },
        fireState = { stateFired++ },
    )

    private fun turnEnds() {
        turnActive = false
        queue.pump()
    }

    @Test
    fun `a prompt typed during a turn waits in the queue until the turn ends`() {
        queue.enqueue("first", emptyList(), "first")
        queue.enqueue("second", emptyList(), "second")

        assertEquals(1, written.size)
        assertEquals(listOf("second"), queue.queued())
        assertEquals(listOf("first"), transcript.entries.map { it.text })

        turnEnds()
        assertEquals(2, written.size)
        assertEquals(emptyList<String>(), queue.queued())
        assertEquals(listOf("first", "second"), transcript.entries.map { it.text })
    }

    @Test
    fun `a queued prompt can be removed before it goes out`() {
        queue.enqueue("first", emptyList(), "first")
        queue.enqueue("second", emptyList(), "second")
        queue.enqueue("third", emptyList(), "third")
        queue.remove(0)
        assertEquals(listOf("third"), queue.queued())

        turnEnds()
        assertEquals(listOf("first", "third"), transcript.entries.map { it.text })
    }

    @Test
    fun `a prompt the process could not take stays queued and leaves no row behind`() {
        writeSucceeds = false
        queue.enqueue("lost?", emptyList(), "lost?")
        assertEquals(listOf("lost?"), queue.queued())
        assertEquals(emptyList<String>(), transcript.entries.map { it.text })
        assertNull(queue.currentUserMessageId)

        writeSucceeds = true
        queue.pump()
        assertEquals(emptyList<String>(), queue.queued())
        assertEquals(listOf("lost?"), transcript.entries.map { it.text })
    }

    @Test
    fun `sending clears the suggestion and a tool call is bound to the turn that sent it`() {
        queue.suggest("Add tests")
        assertEquals("Add tests", queue.suggestion)
        queue.enqueue("go", emptyList(), "go")
        assertNull(queue.suggestion)
        queue.bindTool("tool-1")
        assertEquals(queue.currentUserMessageId, queue.userMessageIdFor("tool-1"))
        queue.forgetTurn()
        assertNull(queue.userMessageIdFor("tool-1"))
    }
}
