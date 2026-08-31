package com.ar13x.jarvis.reminders

import com.ar13x.jarvis.reminders.notification.NudgeActionReceiver
import com.ar13x.jarvis.reminders.notification.catchUpKey
import com.ar13x.jarvis.reminders.notification.nudgeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Two lock-screen buttons that should differ must never be the same intent.
 *
 * `PendingIntent` equality compares intents with `filterEquals` — action, data,
 * component — and **ignores extras entirely**. Two buttons that compare equal
 * collapse into one, and `FLAG_UPDATE_CURRENT` rewrites the survivor's extras,
 * so "Done" on tonight's reminder completes a different occurrence. It fails
 * silently and it fails on a lock screen, where nobody is checking.
 *
 * The scheme this replaced carved request codes into eight-wide slots per
 * notification, and had two faults: `notificationId * 8` overflows `Int` — the
 * id is a 31-bit hash, so `2147483647 * 8` is `-8` — and the offset within a
 * slot reaches 67, running into the next eight occurrences' space.
 *
 * The identity now lives in the data URI, which is the same conclusion
 * `AlarmScheduler` reached for the same reason.
 */
class NudgeActionIdentityTest {

    private val complete = NudgeActionReceiver.ACTION_COMPLETE
    private val extend = NudgeActionReceiver.ACTION_EXTEND

    @Test
    fun `done and extend on the same reminder are different`() {
        assertNotEquals(nudgeKey(42, complete, 0), nudgeKey(42, extend, 15))
    }

    @Test
    fun `the same button on two reminders is different`() {
        assertNotEquals(nudgeKey(42, complete, 0), nudgeKey(43, complete, 0))
    }

    @Test
    fun `two extension lengths are different`() {
        assertNotEquals(nudgeKey(42, extend, 15), nudgeKey(42, extend, 60))
    }

    @Test
    fun `the same button is stable, so re-notifying replaces rather than adds`() {
        assertEquals(nudgeKey(42, extend, 60), nudgeKey(42, extend, 60))
    }

    /**
     * The exhaustive version. The old arithmetic collided at specific id
     * distances — 8, and 2^29 through the overflow — so a spot check would have
     * passed while the bug sat there. This sweeps a realistic matrix instead.
     */
    @Test
    fun `no two distinct buttons collide across a realistic matrix`() {
        val ids = (1L..2_000L) + listOf(Long.MAX_VALUE, 1L shl 29, (1L shl 29) + 8)
        val actions = listOf(complete, extend)
        val lengths = listOf(0, 15, 30, 60, 120)

        val keys = mutableMapOf<String, Triple<Long, String, Int>>()
        for (id in ids) {
            for (action in actions) {
                for (minutes in lengths) {
                    val key = nudgeKey(id, action, minutes)
                    val clash = keys.put(key, Triple(id, action, minutes))
                    assertEquals(
                        "collision on " + key + ": " + clash + " and " + Triple(id, action, minutes),
                        null,
                        clash,
                    )
                }
            }
        }
    }

    /**
     * The specific pair the old scheme confused: ids eight apart, where one
     * button's offset ran into the other's slot.
     */
    @Test
    fun `ids eight apart do not collide`() {
        assertNotEquals(nudgeKey(1000, extend, 60), nudgeKey(1008, complete, 0))
    }

    // --- the catch-up alarm, same rule ----------------------------------------

    @Test
    fun `catch-ups for different occurrences are different`() {
        assertNotEquals(catchUpKey(42), catchUpKey(43))
    }

    /**
     * The catch-up used to be keyed by `occurrenceId.toInt()`, which truncates.
     * Two ids 2^32 apart became one alarm, and cancelling either stopped both.
     */
    @Test
    fun `catch-up keys survive ids that do not fit in an Int`() {
        val id = 42L
        assertNotEquals(catchUpKey(id), catchUpKey(id + (1L shl 32)))
    }

    @Test
    fun `a catch-up is never confused with a nudge button`() {
        assertNotEquals(catchUpKey(42), nudgeKey(42, complete, 0))
    }
}
