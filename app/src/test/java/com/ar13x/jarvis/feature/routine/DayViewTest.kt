package com.ar13x.jarvis.feature.routine

import com.ar13x.jarvis.core.data.RoutineFixture
import com.ar13x.jarvis.core.data.SlotStart
import com.ar13x.jarvis.core.model.LogicalDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val day = LogicalDay(monday, RoutineFixture.theWeek.day(DayOfWeek.MONDAY)!!)

    private fun at(hour: Int, minute: Int = 0) = monday.atTime(hour, minute)

    private fun started(slotId: String, hour: Int, minute: Int) =
        SlotStart(monday, slotId, at(hour, minute))

    private fun rowsAt(now: LocalDateTime, starts: List<SlotStart>) =
        dayView(day, starts, now, isToday = true)

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
        assertTrue(gym.drifted)
        assertEquals(at(20, 15), uni.actualEnd)
    }

    @Test
    fun `starting close to the plan is not drift`() {
        val view = rowsAt(at(10, 0), listOf(started("mon-reskill", 8, 52)))

        assertFalse(view.rows.first { it.slot.id == "mon-reskill" }.drifted)
    }

    @Test
    fun `starting well after the plan is drift`() {
        val view = rowsAt(at(12, 0), listOf(started("mon-reskill", 10, 30)))

        assertTrue(view.rows.first { it.slot.id == "mon-reskill" }.drifted)
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
        val lastWeek = SlotStart(monday.minusWeeks(1), "mon-gym", at(14, 20))

        val view = rowsAt(at(16, 0), listOf(lastWeek))

        assertNull(view.rows.first { it.slot.id == "mon-gym" }.actualStart)
    }

    @Test
    fun `the current slot is the one containing now`() {
        val view = rowsAt(at(14, 48), emptyList())

        assertEquals("mon-gym", view.current?.slot?.id)
        assertEquals("mon-shower", view.upcoming.first().slot.id)
    }
}
