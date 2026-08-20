package com.ar13x.jarvis.reminders

import com.ar13x.jarvis.reminders.alarm.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dedup key (plan §7.2).
 *
 * If a push and a local alarm for the same occurrence do not agree on this
 * number, the user gets the same reminder twice — which is the specific failure
 * §7.2 exists to prevent, and one that only shows up once FCM is added, long
 * after this code was written.
 */
class AlarmDedupTest {

    @Test
    fun `the same task and time always produce the same id`() {
        val a = AlarmScheduler.notificationId(taskId = 12, fireAtMillis = 1_775_000_000_000)
        val b = AlarmScheduler.notificationId(taskId = 12, fireAtMillis = 1_775_000_000_000)
        assertEquals(a, b)
    }

    @Test
    fun `a different time is a different id`() {
        val nine = AlarmScheduler.notificationId(12, 1_775_000_000_000)
        val ten = AlarmScheduler.notificationId(12, 1_775_003_600_000)
        assertNotEquals(nine, ten)
    }

    @Test
    fun `a different task at the same time is a different id`() {
        val gym = AlarmScheduler.notificationId(12, 1_775_000_000_000)
        val bins = AlarmScheduler.notificationId(13, 1_775_000_000_000)
        assertNotEquals(gym, bins)
    }

    /**
     * Some OEM shades misbehave with negative ids, and the sign carries no
     * information — but the obvious hash of a Long is negative half the time.
     */
    @Test
    fun `ids are never negative`() {
        val ids = (1L..500L).flatMap { task ->
            listOf(0L, 1_775_000_000_000, Long.MAX_VALUE / 2).map { at ->
                AlarmScheduler.notificationId(task, at)
            }
        }
        assertTrue("negative id produced", ids.all { it >= 0 })
    }
}
