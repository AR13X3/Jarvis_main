package com.ar13x.jarvis.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Which sessions are recoverable drafts.
 *
 * The bug this fixes was data-shaped: a task conversation that was never
 * confirmed leaves no task, so nothing in the task list pointed at it and
 * backing out lost it — while the session sat on the server, intact and
 * unreachable.
 *
 * Both halves of the test matter. Too narrow and drafts stay lost; too wide and
 * the section fills with the empty sessions that pressing `+` creates.
 */
class UnfinishedTaskTest {

    private fun session(
        kind: SessionKind = SessionKind.Task,
        taskId: Long? = null,
        messages: Int = 4,
    ) = SessionSummary(
        id = "s1",
        kind = kind,
        taskId = taskId,
        title = "Remind me about the job interview",
        updatedAt = Instant.parse("2026-09-01T12:00:00Z"),
        messageCount = messages,
    )

    @Test
    fun `an unbound task session with messages is a draft`() {
        assertTrue(session().isUnfinishedTask)
    }

    /** Confirmed: a task exists and the task list already shows it. */
    @Test
    fun `a bound session is not a draft`() {
        assertFalse(session(taskId = 12).isUnfinishedTask)
    }

    /** Pressing `+` and backing straight out leaves one of these. Not news. */
    @Test
    fun `an empty unbound session is not a draft`() {
        assertFalse(session(messages = 0).isUnfinishedTask)
    }

    /** The Chat tab has its own history; these must not appear in both. */
    @Test
    fun `a general session is never a draft`() {
        assertFalse(session(kind = SessionKind.General).isUnfinishedTask)
        assertFalse(session(kind = SessionKind.General, taskId = null).isUnfinishedTask)
    }
}
