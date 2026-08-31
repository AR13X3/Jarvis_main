package com.ar13x.jarvis.core.model

import com.ar13x.jarvis.core.data.RoutineFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Placing a routine against real time.
 *
 * Almost every test here is about midnight. A routine day runs from waking to
 * sleeping, so midnight falls in the *middle* of Friday, Saturday and Sunday
 * rather than at either end — and the failure mode is silent: bucket by
 * calendar date and three hours of Friday become Saturday, with nothing
 * throwing and every weekend number wrong.
 */
class RoutineClockTest {

    private val routine = RoutineFixture.theWeek

    /** Anchors below assume this. Asserted rather than trusted. */
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)
    private val friday: LocalDate = LocalDate.of(2026, 9, 11)

    @Test
    fun `the anchor dates are the weekdays the other tests assume`() {
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
    }

    // --- durations ------------------------------------------------------------

    @Test
    fun `a slot crossing midnight is ten and a half hours, not a negative number`() {
        val speedway = routine.day(DayOfWeek.FRIDAY)!!.slots.first { it.id == "fri-speedway" }

        assertEquals(630, speedway.durationMinutes)
    }

    @Test
    fun `an ordinary slot is its plain difference`() {
        val gym = routine.day(DayOfWeek.MONDAY)!!.slots.first { it.id == "mon-gym" }

        assertEquals(90, gym.durationMinutes)
    }

    // --- which day is running -------------------------------------------------

    @Test
    fun `half past midnight on Saturday still belongs to Friday`() {
        val at = friday.plusDays(1).atTime(0, 30)

        val logical = routine.logicalDayAt(at)

        assertNotNull(logical)
        assertEquals(DayOfWeek.FRIDAY, logical!!.day.weekday)
        assertEquals(friday, logical.date)
    }

    @Test
    fun `Friday's last slot lands on Saturday's calendar date`() {
        val logical = routine.logicalDayAt(friday.atTime(14, 0))!!
        val windDown = logical.day.slots.first { it.id == "fri-wind" }

        assertEquals(friday.plusDays(1).atTime(1, 0), logical.startOf(windDown))
        assertEquals(friday.plusDays(1).atTime(2, 0), logical.endOf(windDown))
    }

    @Test
    fun `no day is running at three in the morning`() {
        // Friday ends at 02:00 Saturday; Saturday does not start until 08:00.
        // Nothing is underway, and saying so is more honest than picking one.
        assertNull(routine.logicalDayAt(friday.plusDays(1).atTime(3, 0)))
    }

    @Test
    fun `nothing is running before Thursday's late start`() {
        val thursday = monday.plusDays(3)
        assertEquals(DayOfWeek.THURSDAY, thursday.dayOfWeek)

        assertNull(routine.logicalDayAt(thursday.atTime(9, 0)))
        assertNotNull(routine.logicalDayAt(thursday.atTime(10, 30)))
    }

    @Test
    fun `the next day to begin is offered when none is running`() {
        val next = routine.nextDayAfter(friday.plusDays(1).atTime(3, 0))

        assertNotNull(next)
        assertEquals(DayOfWeek.SATURDAY, next!!.day.weekday)
        assertEquals(friday.plusDays(1).atTime(8, 0), next.startsAt)
    }

    // --- splitting around now -------------------------------------------------

    @Test
    fun `the day splits around the current slot`() {
        val at = monday.atTime(14, 48)
        val logical = routine.logicalDayAt(at)!!

        val progress = splitAround(logical, at)

        assertEquals("mon-gym", progress.current?.id)
        assertEquals(
            listOf("mon-wake", "mon-reskill", "mon-lunch", "mon-webdev"),
            progress.past.map { it.id },
        )
        assertEquals("mon-shower", progress.upcoming.first().id)
        assertEquals(5, progress.trackedTotal)
    }

    @Test
    fun `every slot is accounted for exactly once`() {
        val at = monday.atTime(14, 48)
        val logical = routine.logicalDayAt(at)!!

        val progress = splitAround(logical, at)
        val seen = progress.past + listOfNotNull(progress.current) + progress.upcoming

        assertEquals(logical.day.slots.size, seen.size)
        assertEquals(logical.day.slots.map { it.id }.toSet(), seen.map { it.id }.toSet())
    }

    @Test
    fun `a day in the future is entirely upcoming`() {
        // What lets the pager show Thursday while it is still Monday.
        val thursday = LogicalDay(monday.plusDays(3), routine.day(DayOfWeek.THURSDAY)!!)

        val progress = splitAround(thursday, monday.atTime(14, 48))

        assertTrue(progress.past.isEmpty())
        assertNull(progress.current)
        assertEquals(thursday.day.slots.size, progress.upcoming.size)
    }

    @Test
    fun `a day in the past is entirely past`() {
        val progress = splitAround(
            LogicalDay(monday, routine.day(DayOfWeek.MONDAY)!!),
            monday.plusDays(2).atTime(9, 0),
        )

        assertTrue(progress.upcoming.isEmpty())
        assertNull(progress.current)
    }

    @Test
    fun `the current slot is found even when it crosses midnight`() {
        val at = friday.plusDays(1).atTime(0, 5)
        val logical = routine.logicalDayAt(at)!!

        assertEquals("fri-speedway", splitAround(logical, at).current?.id)
    }

    // --- day length -----------------------------------------------------------

    @Test
    fun `day lengths match what the routine claims`() {
        fun minutes(weekday: DayOfWeek) =
            LogicalDay(monday, routine.day(weekday)!!).lengthMinutes

        assertEquals(17 * 60, minutes(DayOfWeek.MONDAY))
        assertEquals(15 * 60, minutes(DayOfWeek.THURSDAY))
        assertEquals(18 * 60, minutes(DayOfWeek.FRIDAY))
    }
}
