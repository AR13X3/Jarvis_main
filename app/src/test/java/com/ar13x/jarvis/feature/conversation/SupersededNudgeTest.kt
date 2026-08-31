package com.ar13x.jarvis.feature.conversation

import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.Message
import com.ar13x.jarvis.core.model.MessageRole
import com.ar13x.jarvis.core.ui.LoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

/**
 * Only the newest overdue card for an occurrence stays answerable.
 *
 * The loop asks up to three times about the same occurrence, and the server
 * resolves a card only when the *user* answers it — an auto-extension supersedes
 * the previous question without resolving it. Seen on device: three identical
 * cards, all with live buttons, when only one referred to a deadline that still
 * existed. Tapping an old one asks the server to extend an occurrence it has
 * already moved.
 */
class SupersededNudgeTest {

    private var nextId = 1L

    private fun nudge(occurrenceId: Long, used: Int) = Message(
        id = nextId++,
        role = MessageRole.Assistant,
        text = "Have you done it?",
        components = listOf(
            AgentComponent.Overdue(
                occurrenceId = occurrenceId,
                taskId = 7,
                extensionsUsed = used,
                extensionsAllowed = 2,
            ),
        ),
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    /** History is oldest-first; the UI state reverses it, so newest wins. */
    private fun stateOf(vararg messages: Message) =
        ConversationUiState(history = LoadState.Ready(messages.toList()))

    @Test
    fun `the newest card for an occurrence is the live one`() {
        val first = nudge(occurrenceId = 88, used = 0)
        val second = nudge(occurrenceId = 88, used = 1)
        val third = nudge(occurrenceId = 88, used = 2)

        val live = stateOf(first, second, third).liveOverdue

        assertEquals(OptionsKey(third.id, 0), live[88])
        assertNotEquals("the first must not stay answerable", OptionsKey(first.id, 0), live[88])
    }

    @Test
    fun `a single card is live`() {
        val only = nudge(occurrenceId = 88, used = 0)

        assertEquals(OptionsKey(only.id, 0), stateOf(only).liveOverdue[88])
    }

    /** Two different tasks nudging in the same session must not silence each other. */
    @Test
    fun `occurrences are tracked separately`() {
        val watch = nudge(occurrenceId = 88, used = 0)
        val washing = nudge(occurrenceId = 99, used = 0)

        val live = stateOf(watch, washing).liveOverdue

        assertEquals(OptionsKey(watch.id, 0), live[88])
        assertEquals(OptionsKey(washing.id, 0), live[99])
    }

    @Test
    fun `a stream with no nudges has none live`() {
        val chat = Message(
            id = 1,
            role = MessageRole.Assistant,
            text = "Sure.",
            createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        )

        assertEquals(emptyMap<Long, OptionsKey>(), stateOf(chat).liveOverdue)
    }
}
