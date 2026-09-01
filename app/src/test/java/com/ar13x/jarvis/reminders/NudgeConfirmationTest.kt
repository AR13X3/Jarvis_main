package com.ar13x.jarvis.reminders

import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.model.TaskStatus
import com.ar13x.jarvis.reminders.notification.NudgeActionReceiver
import com.ar13x.jarvis.reminders.notification.nudgeConfirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A lock-screen action has to say that it worked.
 *
 * Joy pushed "Check Instagram" back three times. The first two succeeded and
 * said nothing; the third was refused for having no extensions left, and that
 * refusal was the only feedback the button had ever produced. The notification
 * is dismissed the instant it is tapped, so a silent success and a tap that
 * missed look exactly alike — and the natural response to "nothing happened" is
 * to do it again, which spends the extension the silence was hiding.
 *
 * So success is now as loud as failure, and it names the new time: the reason to
 * push a reminder back is to move it somewhere, and "pushed back" without saying
 * where leaves you as uncertain as saying nothing.
 */
class NudgeConfirmationTest {

    private val sydney: ZoneId = ZoneId.of("Australia/Sydney")

    private fun task(dueAt: Instant) = Task(
        id = 7,
        title = "Check Instagram",
        dueAt = dueAt,
        dueDate = LocalDate.ofInstant(dueAt, sydney),
        status = TaskStatus.Active,
        createdAt = dueAt,
        updatedAt = dueAt,
    )

    /** 2026-09-01 11:30 in Sydney. */
    private val halfPastEleven: Instant =
        LocalDate.of(2026, 9, 1).atTime(11, 30).atZone(sydney).toInstant()

    @Test
    fun `an extension says how long and when`() {
        val message = nudgeConfirmation(
            NudgeActionReceiver.ACTION_EXTEND,
            minutes = 15,
            task = task(halfPastEleven),
            zone = sydney,
        )

        assertTrue("should name the length: " + message, message!!.contains("15 minutes"))
        assertTrue("should name the new time: " + message, message.contains("11:30"))
    }

    /** "60 minutes" is how a machine says an hour. */
    @Test
    fun `sixty minutes is an hour`() {
        val message = nudgeConfirmation(
            NudgeActionReceiver.ACTION_EXTEND,
            minutes = 60,
            task = task(halfPastEleven),
            zone = sydney,
        )!!

        assertTrue(message.contains("an hour"))
        assertTrue("and not the raw number", !message.contains("60 minutes"))
    }

    @Test
    fun `completing says so`() {
        assertEquals(
            "Marked done.",
            nudgeConfirmation(
                NudgeActionReceiver.ACTION_COMPLETE,
                minutes = 0,
                task = task(halfPastEleven),
                zone = sydney,
            ),
        )
    }

    /**
     * The receiver handles exactly two actions and ignores anything else. A
     * confirmation for an action that did nothing would be a lie.
     */
    @Test
    fun `an unknown action says nothing`() {
        assertNull(
            nudgeConfirmation("com.ar13x.jarvis.SOMETHING_ELSE", 15, task(halfPastEleven), sydney),
        )
    }

    /**
     * The regression, stated as the property rather than as wording: whatever
     * the action, a success is never silent.
     */
    @Test
    fun `every action the receiver handles produces a message`() {
        for (action in listOf(NudgeActionReceiver.ACTION_COMPLETE, NudgeActionReceiver.ACTION_EXTEND)) {
            val message = nudgeConfirmation(action, 15, task(halfPastEleven), sydney)
            assertTrue(action + " produced nothing", !message.isNullOrBlank())
        }
    }
}
