package com.ar13x.jarvis.feature.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Which day the pager shows, and how long a choice lasts.
 *
 * A choice should outlast a scroll but not outlast the day. The first version
 * of this kept the pick forever: browse to Friday on a Tuesday, and the tab was
 * still on Friday next week. The tab exists to answer "what now", so a pick
 * made while a different routine day was running has expired.
 */
class SelectionTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 7)
    private val tuesday: LocalDate = monday.plusDays(1)

    @Test
    fun `with no pick, it follows whatever day is running`() {
        val selection = resolveSelection(
            picked = null,
            runningDate = monday,
            anchorWeekday = DayOfWeek.MONDAY,
            fallback = DayOfWeek.SUNDAY,
        )

        assertEquals(DayOfWeek.MONDAY, selection.weekday)
        assertNull(selection.picked)
    }

    @Test
    fun `a pick holds while the same day is still running`() {
        val selection = resolveSelection(
            picked = DayOfWeek.FRIDAY to monday,
            runningDate = monday,
            anchorWeekday = DayOfWeek.MONDAY,
            fallback = DayOfWeek.MONDAY,
        )

        assertEquals(DayOfWeek.FRIDAY, selection.weekday)
        assertEquals(DayOfWeek.FRIDAY to monday, selection.picked)
    }

    /** The regression. Tomorrow is not the day you were browsing yesterday. */
    @Test
    fun `a pick expires once a different day is running`() {
        val selection = resolveSelection(
            picked = DayOfWeek.FRIDAY to monday,
            runningDate = tuesday,
            anchorWeekday = DayOfWeek.TUESDAY,
            fallback = DayOfWeek.TUESDAY,
        )

        assertEquals(DayOfWeek.TUESDAY, selection.weekday)
        assertNull("the stale pick should have been dropped", selection.picked)
    }

    /**
     * Between sleeping and waking nothing is running, and the pager is showing
     * the day that is about to start. A pick made then has no day to expire
     * against, so it survives until one begins.
     */
    @Test
    fun `a pick made while nothing is running survives until a day starts`() {
        val whileResting = resolveSelection(
            picked = DayOfWeek.SATURDAY to null,
            runningDate = null,
            anchorWeekday = DayOfWeek.SATURDAY,
            fallback = DayOfWeek.SATURDAY,
        )
        assertEquals(DayOfWeek.SATURDAY, whileResting.weekday)
        assertEquals(DayOfWeek.SATURDAY to null, whileResting.picked)

        val onceRunning = resolveSelection(
            picked = whileResting.picked,
            runningDate = monday,
            anchorWeekday = DayOfWeek.MONDAY,
            fallback = DayOfWeek.MONDAY,
        )
        assertEquals(DayOfWeek.MONDAY, onceRunning.weekday)
        assertNull(onceRunning.picked)
    }

    @Test
    fun `with nothing running and nothing picked, it offers the next day`() {
        val selection = resolveSelection(
            picked = null,
            runningDate = null,
            anchorWeekday = DayOfWeek.SATURDAY,
            fallback = DayOfWeek.FRIDAY,
        )

        assertEquals(DayOfWeek.SATURDAY, selection.weekday)
    }

    @Test
    fun `with no routine at all it falls back to the calendar weekday`() {
        val selection = resolveSelection(
            picked = null,
            runningDate = null,
            anchorWeekday = null,
            fallback = DayOfWeek.WEDNESDAY,
        )

        assertEquals(DayOfWeek.WEDNESDAY, selection.weekday)
    }
}
