package com.ar13x.jarvis.reminders

import com.ar13x.jarvis.reminders.alarm.AlarmScheduler
import com.ar13x.jarvis.reminders.routine.RoutineAlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A routine slot alarm's identity (§9.6), pinned the way [AlarmIdentityTest]
 * pins a reminder's — because BUILD_NOTES §14 records this codebase getting the
 * same thing wrong **three times in one package**, twice by people who had read
 * the comment warning them.
 *
 * §9.6 states the requirement directly: *"a slot alarm needs a key that cannot
 * collide with an occurrence alarm."*
 */
class SlotAlarmIdentityTest {

    private val friday: LocalDate = LocalDate.of(2026, 9, 4)

    @Test
    fun `the key is the logical day and the slot, and nothing else`() {
        // The §14 trap, stated as a test: a slot's TIME must not be in its key.
        // A routine edit moving gym from 08:00 to 09:00 has to re-arm the SAME
        // PendingIntent, or FLAG_UPDATE_CURRENT has nothing to update and both
        // alarms stay armed. That is exactly how a reminder once fired twice,
        // once at an hour Joy had already moved.
        assertEquals(
            RoutineAlarmScheduler.key(friday, "fri-gym"),
            RoutineAlarmScheduler.key(friday, "fri-gym"),
        )
        assertTrue(RoutineAlarmScheduler.key(friday, "fri-gym").endsWith("/fri-gym"))
    }

    @Test
    fun `the same slot on two days is two alarms`() {
        assertNotEquals(
            RoutineAlarmScheduler.key(friday, "fri-gym"),
            RoutineAlarmScheduler.key(friday.plusDays(7), "fri-gym"),
        )
        assertNotEquals(
            RoutineAlarmScheduler.requestCode(friday, "fri-gym"),
            RoutineAlarmScheduler.requestCode(friday.plusDays(7), "fri-gym"),
        )
    }

    @Test
    fun `two slots on the same day are two alarms`() {
        assertNotEquals(
            RoutineAlarmScheduler.key(friday, "fri-gym"),
            RoutineAlarmScheduler.key(friday, "fri-webdev"),
        )
    }

    @Test
    fun `a slot key can never be an occurrence key`() {
        // Different authority. `jarvis://slot/...` against `jarvis://occurrence/...`
        // — the two schemes cannot produce the same string for any inputs,
        // which is what stops a routine slot and a reminder collapsing into one
        // PendingIntent.
        val slot = RoutineAlarmScheduler.key(friday, "fri-gym")

        assertTrue(slot.startsWith("jarvis://slot/"))
        assertTrue(!slot.startsWith("jarvis://occurrence/"))
    }

    @Test
    fun `the request code is stable and never negative`() {
        // Some OEM notification shades behave oddly with negative ids, and the
        // sign carries no information — the same reasoning as `alarmKey`.
        for (slotId in listOf("fri-gym", "sat-speedway", "wed-uni", "thu-cook")) {
            val code = RoutineAlarmScheduler.requestCode(friday, slotId)
            assertEquals(code, RoutineAlarmScheduler.requestCode(friday, slotId))
            assertTrue(slotId + " produced a negative request code", code >= 0)
        }
    }

    @Test
    fun `the notification id is the request code, deliberately`() {
        // The opposite of the reminder rule, and it is worth stating rather than
        // leaving as a coincidence. A reminder's notification id must vary with
        // the fire time so a PUSH and a LOCAL ALARM for one firing collapse into
        // one notification. Nothing pushes routine slots, so there is no second
        // source to collapse with, and one slot on one day is one notification.
        assertEquals(
            RoutineAlarmScheduler.requestCode(friday, "fri-gym"),
            RoutineAlarmScheduler.notificationId(friday, "fri-gym"),
        )
    }

    @Test
    fun `a slot id with awkward characters stays one path segment`() {
        // Not reachable from today's ids, which are all `fri-speedway` shaped.
        // Asserted anyway: the day one arrives with a slash in it, a key that
        // silently gained a path segment would still LOOK like a valid URI and
        // two different slots could collide.
        val encoded = RoutineAlarmScheduler.encodeSlotId("a/b c")

        assertEquals("a%2Fb%20c", encoded)
        assertTrue(!encoded.contains("/"))
        assertTrue(!encoded.contains(" "))
    }

    @Test
    fun `ordinary slot ids are left exactly as they are`() {
        // Encoding that mangled a normal id would change every key silently and
        // orphan every armed alarm on the next release.
        assertEquals("fri-speedway", RoutineAlarmScheduler.encodeSlotId("fri-speedway"))
        assertEquals("wed-uni", RoutineAlarmScheduler.encodeSlotId("wed-uni"))
    }

    @Test
    fun `slot and occurrence request codes are allowed to collide, and it does not matter`() {
        // Stated so nobody "fixes" it. PendingIntent identity is the request
        // code AND the intent, and the intents differ by data URI and by target
        // component. Two equal request codes on different intents are two
        // different PendingIntents.
        val slotCode = RoutineAlarmScheduler.requestCode(friday, "fri-gym")
        val occurrenceCode = AlarmScheduler.alarmKey(42)

        // No assertion that they differ — only that both are usable.
        assertTrue(slotCode >= 0)
        assertTrue(occurrenceCode >= 0)
    }
}
