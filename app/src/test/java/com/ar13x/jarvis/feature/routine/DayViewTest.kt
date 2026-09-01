package com.ar13x.jarvis.feature.routine

import com.ar13x.jarvis.core.data.RoutineFixture
import com.ar13x.jarvis.core.data.SlotStart
import com.ar13x.jarvis.core.model.LogicalDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Start-only logging (v2 plan §4.4).
 *
 * There is no stop button, so a slot's real end is the next slot's start. That
 * is one tap instead of two, and it records doing things out of order without
 * anyone having to declare that an order was broken — which is the variation the
 * feature exists to show.
 */
class DayViewTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 7)
    private val VERSION = "2026-09-01"
    private val day = LogicalDay(monday, RoutineFixture.theWeek.day(DayOfWeek.MONDAY)!!)

    private fun at(hour: Int, minute: Int = 0) = monday.atTime(hour, minute)

    private fun started(slotId: String, hour: Int, minute: Int) =
        SlotStart(VERSION, monday, slotId, at(hour, minute))

    /**
     * Threads the fixture's tolerance in, the way the ViewModel threads the
     * routine's. Passed explicitly rather than left to default, and that is the
     * point: `drifted` is *unknown* without one, so a test that quietly omitted
     * it would assert against null and stop testing drift at all.
     */
    private fun rowsAt(now: LocalDateTime, starts: List<SlotStart>) =
        dayView(
            logical = day,
            starts = starts,
            at = now,
            isToday = true,
            driftToleranceMinutes = RoutineFixture.theWeek.driftToleranceMinutes,
        )

    @Test
    fun `the next start is what ends the previous slot`() {
        val view = rowsAt(
            at(15, 0),
            listOf(started("mon-reskill", 8, 50), started("mon-webdev", 12, 20)),
        )

        val reskill = view.rows.first { it.slot.id == "mon-reskill" }

        assertEquals(at(8, 50), reskill.actualStart)
        assertEquals(at(12, 20), reskill.actualEnd)
    }

    @Test
    fun `a slot with nothing started after it is still open`() {
        val view = rowsAt(at(10, 0), listOf(started("mon-reskill", 8, 50)))

        val reskill = view.rows.first { it.slot.id == "mon-reskill" }

        assertEquals(at(8, 50), reskill.actualStart)
        assertNull(reskill.actualEnd)
    }

    @Test
    fun `the end of the day closes whatever was left open`() {
        // One missed tap at 11pm should not leave a fourteen-hour Reskill block.
        val view = rowsAt(monday.plusDays(1).atTime(9, 0), listOf(started("mon-meet", 21, 5)))

        val meeting = view.rows.first { it.slot.id == "mon-meet" }

        assertEquals(day.endsAt, meeting.actualEnd)
        assertEquals(monday.plusDays(1).atTime(1, 0), meeting.actualEnd)
    }

    @Test
    fun `starting out of order is recorded, not corrected`() {
        // Gym after uni. Nothing here declares that wrong — it is the variation.
        val view = rowsAt(
            at(21, 0),
            listOf(started("mon-uni", 16, 30), started("mon-gym", 20, 15)),
        )

        val gym = view.rows.first { it.slot.id == "mon-gym" }
        val uni = view.rows.first { it.slot.id == "mon-uni" }

        assertEquals(at(20, 15), gym.actualStart)
        assertEquals(true, gym.drifted)
        assertEquals(at(20, 15), uni.actualEnd)
    }

    @Test
    fun `starting close to the plan is not drift`() {
        val view = rowsAt(at(10, 0), listOf(started("mon-reskill", 8, 52)))

        assertEquals(false, view.rows.first { it.slot.id == "mon-reskill" }.drifted)
    }

    @Test
    fun `starting well after the plan is drift`() {
        val view = rowsAt(at(12, 0), listOf(started("mon-reskill", 10, 30)))

        assertEquals(true, view.rows.first { it.slot.id == "mon-reskill" }.drifted)
    }

    @Test
    fun `starting early counts, because the deviation is absolute`() {
        // Matches the gateway's DriftingSlot, which is |actual - planned|. If the
        // two disagreed, the screen and the dashboard would disagree about the
        // same slot and neither would be wrong on its own terms.
        val view = rowsAt(at(10, 0), listOf(started("mon-reskill", 8, 0)))

        assertEquals(true, view.rows.first { it.slot.id == "mon-reskill" }.drifted)
    }

    @Test
    fun `with no tolerance received, drift is unknown rather than false`() {
        // The whole reason `drifted` is nullable. Before this, SlotRow hardcoded
        // 15 and would happily return `false` for a routine it had no threshold
        // for — a verdict computed from a number the client invented, which is
        // how grace_minutes went wrong on the occurrence side.
        //
        // Null must not collapse to false anywhere: an unmeasurable slot is not
        // an on-time one, and the row shows no verdict at all.
        val view = dayView(
            logical = day,
            starts = listOf(started("mon-reskill", 10, 30)),
            at = at(12, 0),
            isToday = true,
            driftToleranceMinutes = null,
        )

        val reskill = view.rows.first { it.slot.id == "mon-reskill" }

        assertNull(reskill.drifted)
        // ...and the start itself is still recorded. Not knowing whether it
        // drifted says nothing about whether it happened.
        assertEquals(at(10, 30), reskill.actualStart)
    }

    @Test
    fun `a slot that was never started is not drifting, even with a tolerance`() {
        // `false` here is a real answer, not a stand-in for unknown: there is a
        // threshold, and nothing deviated from the plan because nothing began.
        // `unrecorded` is the word for that, and it is asserted separately.
        val view = rowsAt(at(16, 0), emptyList())

        assertEquals(false, view.rows.first { it.slot.id == "mon-gym" }.drifted)
    }

    @Test
    fun `a tracked slot whose time passed with no start is unrecorded`() {
        val view = rowsAt(at(16, 0), emptyList())

        val gym = view.rows.first { it.slot.id == "mon-gym" }

        assertTrue(gym.unrecorded)
        assertEquals(3, view.unrecordedCount)
    }

    @Test
    fun `scaffold is never unrecorded, however long it has been`() {
        val view = rowsAt(at(23, 0), emptyList())

        assertTrue(view.rows.filter { it.slot.id.endsWith("lunch") }.none { it.unrecorded })
        assertTrue(view.rows.none { it.unrecorded && it.slot.kind != com.ar13x.jarvis.core.model.SlotKind.Tracked })
    }

    @Test
    fun `counts only ever speak about tracked slots`() {
        val view = rowsAt(at(12, 0), listOf(started("mon-reskill", 8, 50)))

        assertEquals(5, view.trackedTotal)
        assertEquals(1, view.startedCount)
    }

    @Test
    fun `starts recorded against another day are ignored`() {
        // Slot ids repeat every week, so the date is what separates them.
        val lastWeek = SlotStart(VERSION, monday.minusWeeks(1), "mon-gym", at(14, 20))

        val view = rowsAt(at(16, 0), listOf(lastWeek))

        assertNull(view.rows.first { it.slot.id == "mon-gym" }.actualStart)
    }

    @Test
    fun `the current slot is the one containing now`() {
        val view = rowsAt(at(14, 48), emptyList())

        assertEquals("mon-gym", view.current?.slot?.id)
        assertEquals("mon-shower", view.upcoming.first().slot.id)
    }

    // --- splitting the day ----------------------------------------------------
    //
    // These moved here from RoutineClockTest, which was testing a `splitAround`
    // that production code never called while `dayView` reimplemented the same
    // logic beside it. Two implementations, and the tests were guarding the one
    // that could not break anything.

    @Test
    fun `the day splits around the current slot`() {
        val view = rowsAt(at(14, 48), emptyList())

        assertEquals(
            listOf("mon-wake", "mon-reskill", "mon-lunch", "mon-webdev"),
            view.past.map { it.slot.id },
        )
        assertEquals("mon-gym", view.current?.slot?.id)
    }

    @Test
    fun `every slot is accounted for exactly once`() {
        val view = rowsAt(at(14, 48), emptyList())
        val seen = view.past + listOfNotNull(view.current) + view.upcoming

        assertEquals(day.day.slots.size, seen.size)
        assertEquals(day.day.slots.map { it.id }.toSet(), seen.map { it.slot.id }.toSet())
    }

    @Test
    fun `a day in the future is entirely upcoming`() {
        // What lets the pager show Thursday while it is still Monday.
        val thursday = LogicalDay(
            monday.plusDays(3),
            RoutineFixture.theWeek.day(DayOfWeek.THURSDAY)!!,
        )

        val view = dayView(thursday, emptyList(), at(14, 48), isToday = false)

        assertTrue(view.past.isEmpty())
        assertNull(view.current)
        assertEquals(thursday.day.slots.size, view.upcoming.size)
    }

    @Test
    fun `a day in the past is entirely past`() {
        val view = dayView(day, emptyList(), monday.plusDays(2).atTime(9, 0), isToday = false)

        assertTrue(view.upcoming.isEmpty())
        assertNull(view.current)
    }

    @Test
    fun `the current slot is found even when it crosses midnight`() {
        val friday = LocalDate.of(2026, 9, 11)
        val logical = LogicalDay(friday, RoutineFixture.theWeek.day(DayOfWeek.FRIDAY)!!)

        val view = dayView(logical, emptyList(), friday.plusDays(1).atTime(0, 5), isToday = true)

        assertEquals("fri-speedway", view.current?.slot?.id)
    }
}
