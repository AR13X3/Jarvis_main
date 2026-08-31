package com.ar13x.jarvis.reminders

import com.ar13x.jarvis.reminders.alarm.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An alarm's identity, which is **not** a notification's.
 *
 * Joy reported the symptom by hand: a reminder set for 11:00, moved to 11:30,
 * and both fired. The mirror was fixed at the time to replace its window rather
 * than merge it, which stopped the server's copy duplicating — but arming is a
 * separate question, and an alarm the app never cancels fires whatever the
 * mirror holds.
 *
 * The cause was one number doing two jobs. `notificationId` is built from the
 * fire time, which is correct for collapsing a push and a local alarm for the
 * same firing — and exactly wrong as a `PendingIntent` request code, because a
 * reschedule then produces a *different* PendingIntent. `FLAG_UPDATE_CURRENT`
 * had nothing to update, and `reconcile` did not cancel the old alarm either:
 * the occurrence is still in `current`, under the same id. Two alarms.
 *
 * So the two properties are pinned separately and against each other, because
 * the tempting simplification is to make them one function again.
 */
class AlarmIdentityTest {

    /** The regression. Same occurrence, new time, same alarm. */
    @Test
    fun `moving a reminder does not change its alarm identity`() {
        val atEleven = AlarmScheduler.alarmKey(occurrenceId = 42)
        val atHalfPast = AlarmScheduler.alarmKey(occurrenceId = 42)

        assertEquals(atEleven, atHalfPast)
    }

    /**
     * The distinction, stated as a test so that collapsing the two functions
     * back into one fails here rather than on someone's lock screen.
     */
    @Test
    fun `the alarm key ignores time while the notification id depends on it`() {
        val eleven = 1_788_000_000_000L
        val halfPast = eleven + 30 * 60_000L

        assertEquals(
            "an alarm must keep its identity across a reschedule",
            AlarmScheduler.alarmKey(42),
            AlarmScheduler.alarmKey(42),
        )
        assertNotEquals(
            "a notification must not collapse two different firings",
            AlarmScheduler.notificationId(7, eleven),
            AlarmScheduler.notificationId(7, halfPast),
        )
    }

    @Test
    fun `different occurrences have different alarm keys`() {
        assertNotEquals(AlarmScheduler.alarmKey(42), AlarmScheduler.alarmKey(43))
    }

    /**
     * Same reason as `notificationId`: some OEM shades misbehave with negative
     * ids, and a bare `hashCode` is negative about half the time.
     */
    @Test
    fun `alarm keys are never negative`() {
        val keys = (1L..1000L).map(AlarmScheduler::alarmKey) +
            listOf(0L, Long.MAX_VALUE, Long.MIN_VALUE).map(AlarmScheduler::alarmKey)

        assertTrue("negative key produced", keys.all { it >= 0 })
    }

    /**
     * Gateway ids are sequential, so the range that will actually be used must
     * not collide. Beyond it, the data URI on the intent still separates two
     * occurrences even if their keys met — but relying on that is not a plan.
     */
    @Test
    fun `realistic occurrence ids do not collide`() {
        val keys = (1L..200_000L).map(AlarmScheduler::alarmKey)

        assertEquals(keys.size, keys.toSet().size)
    }
}
