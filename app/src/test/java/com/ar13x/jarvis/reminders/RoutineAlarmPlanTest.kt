package com.ar13x.jarvis.reminders

import com.ar13x.jarvis.core.data.RoutineFixture
import com.ar13x.jarvis.core.model.SlotKind
import com.ar13x.jarvis.core.model.trackedSlotAlarms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Which routine slots get an alarm, and on which logical day (§9.6).
 *
 * Two things are pinned here and each has already gone wrong once in this
 * codebase's history, in the reminder half:
 *
 * 1. **Tracked slots only.** A routine partitions the day end to end, so arming
 *    every slot is roughly eighty notifications a week. §9.6 is explicit that
 *    this does not get tuned down later — it gets the feature switched off.
 *
 * 2. **A routine day is not a calendar day.** Friday's Speedway slot runs 13:45
 *    to 00:15 and two more slots follow it. Anything that buckets by calendar
 *    date files three hours of Friday night under Saturday, which is the bug
 *    `RoutineClock` exists to prevent.
 */
class RoutineAlarmPlanTest {

    private val week = RoutineFixture.theWeek

    /** A Monday. 2026-09-07 is a Monday; the fixture's week repeats. */
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)

    @Test
    fun `the fixture week is anchored where these tests think it is`() {
        // If this drifts, every date below means something else and the rest of
        // the file starts asserting nonsense while still passing.
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
    }

    @Test
    fun `only tracked slots are armed`() {
        val alarms = week.trackedSlotAlarms(monday.atTime(7, 0), Duration.ofHours(20))

        assertTrue("expected some alarms on a Monday", alarms.isNotEmpty())

        val trackedIds = week.days
            .flatMap { it.slots }
            .filter { it.kind == SlotKind.Tracked }
            .mapTo(mutableSetOf()) { it.id }

        for (alarm in alarms) {
            assertTrue(alarm.slotId + " is not a tracked slot", alarm.slotId in trackedIds)
        }
    }

    @Test
    fun `scaffold, buffer and free slots never appear`() {
        val alarms = week.trackedSlotAlarms(monday.atTime(7, 0), Duration.ofHours(48))
        val armed = alarms.mapTo(mutableSetOf()) { it.slotId }

        // Named rather than derived, so the test states the claim rather than
        // restating the implementation.
        assertFalse("mon-wake is scaffold", "mon-wake" in armed)
        assertFalse("mon-dinner is scaffold", "mon-dinner" in armed)
        assertFalse("mon-breather is free", "mon-breather" in armed)
        assertFalse("mon-free is free", "mon-free" in armed)
    }

    @Test
    fun `nothing already past is armed`() {
        // Monday's tracked morning block starts 08:45. At 12:00 it has gone, and
        // arming it would fire immediately for a moment that has passed — the
        // same guard `AlarmScheduler.reconcile` makes for occurrences.
        val alarms = week.trackedSlotAlarms(monday.atTime(12, 0), Duration.ofHours(12))

        assertTrue(alarms.all { it.at.isAfter(monday.atTime(12, 0)) })
        assertFalse("mon-reskill" in alarms.map { it.slotId })
    }

    @Test
    fun `the window is honoured at both ends`() {
        val now = monday.atTime(7, 0)
        val alarms = week.trackedSlotAlarms(now, Duration.ofHours(4))

        assertTrue(alarms.isNotEmpty())
        assertTrue(alarms.all { !it.at.isAfter(now.plusHours(4)) })
    }

    @Test
    fun `alarms come back soonest first`() {
        val alarms = week.trackedSlotAlarms(monday.atTime(0, 30), Duration.ofHours(48))

        assertEquals(alarms.sortedBy { it.at }, alarms)
    }

    // --- the midnight-crossing case, which is the whole reason for LogicalDay -

    @Test
    fun `Friday's Speedway slot is armed on Friday and runs past midnight`() {
        val friday = monday.plusDays(4)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)

        val speedway = week
            .trackedSlotAlarms(friday.atTime(7, 0), Duration.ofHours(12))
            .single { it.slotId == "fri-speedway" }

        assertEquals(friday, speedway.on)
        assertEquals(friday.atTime(13, 45), speedway.at)
        // 13:45 to 00:15 is ten and a half hours, and it ends on SATURDAY's
        // date while still belonging to Friday.
        assertEquals(friday.plusDays(1).atTime(0, 15), speedway.endsAt)
        assertEquals(friday, speedway.on)
    }

    @Test
    fun `a slot after midnight still belongs to the day that began yesterday`() {
        // 00:30 on Saturday. Friday's logical day runs to 02:00 and is still
        // running, so the scan has to start at YESTERDAY — starting at today is
        // the bug this asserts against.
        val saturdayEarly = monday.plusDays(5).atTime(0, 30)

        val alarms = week.trackedSlotAlarms(saturdayEarly, Duration.ofHours(12))

        // Saturday's own tracked morning is inside the window and is filed under
        // Saturday...
        val gym = alarms.single { it.slotId == "sat-gym" }
        assertEquals(monday.plusDays(5), gym.on)

        // ...and every alarm's logical day is the day whose plan contains it,
        // never simply the calendar date of its start time.
        for (alarm in alarms) {
            val day = week.day(alarm.on.dayOfWeek)!!
            assertTrue(
                alarm.slotId + " filed under " + alarm.on.dayOfWeek,
                day.slots.any { it.id == alarm.slotId },
            )
        }
    }

    @Test
    fun `the day end carried with a slot is its own logical day's end`() {
        val friday = monday.plusDays(4)
        val speedway = week
            .trackedSlotAlarms(friday.atTime(7, 0), Duration.ofHours(12))
            .single { it.slotId == "fri-speedway" }

        // Friday ends at 02:00 on Saturday's date. This is what the Start action
        // compares against to decide `clamped`, so it has to be the logical
        // day's end and not midnight.
        assertEquals(friday.plusDays(1).atTime(2, 0), speedway.dayEndsAt)
        assertTrue(speedway.dayEndsAt.isAfter(speedway.at))
    }

    @Test
    fun `a 48 hour window covers more than one day without duplicating a slot`() {
        val alarms = week.trackedSlotAlarms(monday.atTime(7, 0), Duration.ofHours(48))

        val keys = alarms.map { it.on to it.slotId }
        assertEquals("no (day, slot) pair may appear twice", keys.size, keys.toSet().size)
        assertTrue("48 hours should reach a second day", alarms.map { it.on }.distinct().size >= 2)
    }

    @Test
    fun `a window longer than two days actually reaches its far end`() {
        // The regression. The day walk was fixed at `-1..3`, which is correct
        // for the 48 hours every caller passes and silently short for anything
        // longer -- `RoutineAlarmScheduler`'s cancel sweep asks for four days
        // and never saw the last of them. It under-cancelled rather than
        // misfiring, so nothing visibly broke; a window argument that is quietly
        // capped is the kind of thing that gets trusted with a bigger number.
        val now = monday.atTime(7, 0)
        val fourDays = week.trackedSlotAlarms(now, Duration.ofDays(4))

        assertTrue(
            "a four-day window should span four distinct logical days",
            fourDays.map { it.on }.distinct().size >= 4,
        )
        assertTrue(
            "nothing may fall outside the window it was asked for",
            fourDays.all { !it.at.isAfter(now.plusDays(4)) },
        )
        assertTrue(
            "a four-day window must contain strictly more than a two-day one",
            fourDays.size > week.trackedSlotAlarms(now, Duration.ofDays(2)).size,
        )
    }

    @Test
    fun `an empty window arms nothing rather than everything`() {
        assertTrue(
            week.trackedSlotAlarms(monday.atTime(7, 0), Duration.ZERO).isEmpty(),
        )
    }

    @Test
    fun `about four tracked slots a day, which is the number this feature lives or dies on`() {
        // §9.6: "about 3.7 a day". If a change to the fixture or the filter made
        // this ten, the feature would be eighty interruptions a week and would
        // be switched off — so the count itself is the assertion.
        val alarms = week.trackedSlotAlarms(monday.atTime(0, 1), Duration.ofHours(24))

        assertTrue(
            "expected roughly a day's tracked slots, got " + alarms.size,
            alarms.size in 2..6,
        )
    }

    @Test
    fun `a start at 3am on a Sunday is unarmed rather than misfiled`() {
        // 03:00 Sunday is in one of the 48 hours a week that belong to no
        // logical day — Saturday's ended at 02:00 and Sunday's begins at 08:00.
        // Nothing should be armed for the gap itself, and the next alarm should
        // be Sunday's, not Saturday's.
        val gap = monday.plusDays(6).atTime(3, 0)
        val next = week.trackedSlotAlarms(gap, Duration.ofHours(12)).firstOrNull()

        assertTrue(next != null)
        assertTrue(next!!.at.isAfter(gap))
        assertEquals(DayOfWeek.SUNDAY, next.on.dayOfWeek)
    }

    private fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime =
        LocalDateTime.of(this, java.time.LocalTime.of(hour, minute))
}
