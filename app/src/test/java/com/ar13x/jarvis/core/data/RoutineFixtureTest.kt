package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.CategoryClass
import com.ar13x.jarvis.core.model.LogicalDay
import com.ar13x.jarvis.core.model.durationMinutes
import com.ar13x.jarvis.core.model.weeklyMinutesByCategory
import com.ar13x.jarvis.core.model.weeklyMinutesByClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Proof that the fixture is a transcription of `docs/the-week.html` and not an
 * approximation of it.
 *
 * The source file states its own weekly totals, so they can be checked rather
 * than eyeballed: a mistyped time changes a total, and the test names which
 * category moved. Eighty slots typed by hand is exactly the sort of thing that
 * is wrong in one place and looks right everywhere.
 */
class RoutineFixtureTest {

    private val routine = RoutineFixture.theWeek

    /** Hours, as printed under "Weekly totals" in the source. */
    private val stated = mapOf(
        "speedway" to 31.5,
        "free" to 23.25,
        "life" to 20.25,
        "reskill" to 20.0,
        "uni" to 10.0,
        "webdev" to 9.0,
        "gym" to 6.0,
    )

    @Test
    fun `every weekly category total matches the source file`() {
        val actual = routine.weeklyMinutesByCategory()

        for ((id, hours) in stated) {
            assertEquals("category $id", hours, actual.getValue(id) / 60.0, 0.0)
        }
        assertEquals(stated.keys, actual.keys)
    }

    @Test
    fun `the headline split matches the source file`() {
        val byClass = routine.weeklyMinutesByClass()

        assertEquals(76.5, byClass.getValue(CategoryClass.Committed) / 60.0, 0.0)
        assertEquals(20.25, byClass.getValue(CategoryClass.Upkeep) / 60.0, 0.0)
        assertEquals(23.25, byClass.getValue(CategoryClass.Free) / 60.0, 0.0)
    }

    @Test
    fun `the week is a hundred and twenty waking hours`() {
        assertEquals(120.0, routine.weeklyMinutesByCategory().values.sum() / 60.0, 0.0)
    }

    /**
     * The defining property (v2 plan §4.1): a routine *partitions* the day. If
     * slots merely sat in a day rather than tiling it, the totals above would be
     * arithmetic over an arbitrary subset and would mean nothing.
     */
    @Test
    fun `each day's slots tile it end to end with no gaps and no overlaps`() {
        val anchor = LocalDate.of(2026, 9, 7)

        for (day in routine.days) {
            val logical = LogicalDay(anchor, day)
            var cursor = logical.startsAt

            for (slot in day.slots) {
                assertEquals(
                    "${day.weekday} has a gap or overlap before '${slot.label}'",
                    cursor,
                    logical.startOf(slot),
                )
                cursor = logical.endOf(slot)
            }
            assertEquals("${day.weekday} does not end where it says it does", logical.endsAt, cursor)
        }
    }

    @Test
    fun `slot durations sum to the declared length of every day`() {
        for (day in routine.days) {
            val summed = day.slots.sumOf { it.durationMinutes }
            assertEquals(
                day.weekday.toString(),
                LogicalDay(LocalDate.of(2026, 9, 7), day).lengthMinutes,
                summed,
            )
        }
    }

    @Test
    fun `every slot names a category that exists`() {
        val known = routine.categories.map { it.id }.toSet()

        for (day in routine.days) {
            for (slot in day.slots) {
                assertTrue("${slot.id} names unknown category ${slot.categoryId}", slot.categoryId in known)
            }
        }
    }

    @Test
    fun `slot ids are unique across the week`() {
        val ids = routine.days.flatMap { day -> day.slots.map { it.id } }

        assertEquals(ids.size, ids.toSet().size)
    }

    /**
     * The point of the kinds (§4.3): about five tickable things a day, not
     * twelve. If this drifts upward the day view has quietly become the noise
     * it exists to avoid.
     */
    @Test
    fun `no day asks for more than six things to be started`() {
        for (day in routine.days) {
            val tracked = day.trackedSlots.size
            assertTrue("${day.weekday} tracks $tracked slots", tracked in 2..6)
        }
    }

    @Test
    fun `Wednesday's protected buffer is a buffer slot, not a tracked one`() {
        val buffer = routine.day(DayOfWeek.WEDNESDAY)!!.slots.first { it.id == "wed-buffer" }

        // "Keep empty" inverts the success condition — counting it like a
        // tracked slot would score every honest Wednesday as a failure.
        assertEquals(com.ar13x.jarvis.core.model.SlotKind.Buffer, buffer.kind)
    }
}
