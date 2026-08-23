package com.ar13x.jarvis.feature.tasks.list

import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.model.TaskStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * The Overdue section.
 *
 * The rule it must not break is §3.2: no calendar day is derived from a
 * timestamp here. Comparing two instants is timezone-independent, which is why
 * the boundary test below holds with the device in Sydney and the data in UTC —
 * the same setup that makes day-derivation wrong leaves instant comparison
 * untouched.
 */
class OverdueSectionTest {

    private val sydney: ZoneId = ZoneId.of("Australia/Sydney")
    private lateinit var original: TimeZone

    @Before
    fun setUp() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(sydney))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(original)
    }

    private val now: Instant = Instant.parse("2026-08-22T08:40:00Z")

    private fun task(
        id: Long,
        dueAt: Instant,
        status: TaskStatus = TaskStatus.Active,
        recurrence: String? = null,
        nextFireAt: Instant? = dueAt,
    ) = Task(
        id = id,
        title = "Task $id",
        description = "",
        dueAt = dueAt,
        dueDate = LocalDate.ofInstant(dueAt, sydney),
        isPriority = false,
        recurrence = recurrence,
        recurrenceText = if (recurrence != null) "Every Tuesday" else null,
        nextFireAt = nextFireAt,
        status = status,
        dueToday = true,
        createdAt = now,
        updatedAt = now,
    )

    /** A repeating rule, anchored in the past and firing next at [nextFireAt]. */
    private fun recurring(id: Long, nextFireAt: Instant?, anchor: Instant = now.minusSeconds(60L * 60 * 24 * 60)) =
        task(id, dueAt = anchor, recurrence = "FREQ=DAILY", nextFireAt = nextFireAt)

    @Test
    fun `a past deadline still active is overdue`() {
        assertTrue(task(1, now.minusSeconds(300)).isOverdue(now))
    }

    @Test
    fun `a future deadline is not`() {
        assertFalse(task(1, now.plusSeconds(300)).isOverdue(now))
    }

    /** The server's own word for "fired, waiting on you" — authoritative. */
    @Test
    fun `awaiting is overdue whatever the clock says`() {
        assertTrue(task(1, now.plusSeconds(3600), status = TaskStatus.Awaiting).isOverdue(now))
    }

    /**
     * A lapse is a record, not a demand. Mixing them in would make the section
     * something you learn to ignore.
     */
    @Test
    fun `incomplete, completed and cancelled are excluded`() {
        val past = now.minusSeconds(3600)
        assertFalse(task(1, past, status = TaskStatus.Incomplete).isOverdue(now))
        assertFalse(task(2, past, status = TaskStatus.Completed).isOverdue(now))
        assertFalse(task(3, past, status = TaskStatus.Cancelled).isOverdue(now))
    }

    @Test
    fun `the section is soonest-first and deduplicated across sources`() {
        val late = task(1, now.minusSeconds(60))
        val later = task(2, now.minusSeconds(7200))
        val fine = task(3, now.plusSeconds(3600))

        val content = TaskListContent(
            // A task legitimately appears in more than one section, and must be
            // listed once here.
            priority = listOf(late),
            recurring = emptyList(),
            all = listOf(late, later, fine),
        )

        assertEquals(listOf(2L, 1L), content.overdue(now).map { it.id })
    }

    /**
     * §3.2's boundary from the other side. 08:40 UTC is 6:40 pm in Sydney — the
     * case that breaks day-derivation. Instant comparison does not care.
     */
    @Test
    fun `the UTC-Sydney boundary does not affect it`() {
        assertTrue(task(1, now.minusSeconds(1)).isOverdue(now))
        assertFalse(task(2, now.plusSeconds(1)).isOverdue(now))
    }

    /**
     * The defect this section shipped with in 0.1.3.
     *
     * A recurring rule keeps its original `due_at` as the series anchor forever
     * and stays `active` between firings, so measuring it against the anchor
     * put every repeating task in Overdue permanently — a daily task created in
     * June sat here for the rest of its life. Reported by gw03 in document 08
     * §4.3, where the server had already declined the same literal reading.
     */
    @Test
    fun `a recurring rule between firings is not overdue`() {
        assertFalse(recurring(1, nextFireAt = now.plusSeconds(3600)).isOverdue(now))
    }

    /** The gap still applies to a repeating task — it is just measured on the firing. */
    @Test
    fun `a recurring rule whose firing has passed is overdue`() {
        assertTrue(recurring(1, nextFireAt = now.minusSeconds(60)).isOverdue(now))
    }

    /** Nothing scheduled means no moment to have missed. The anchor is not a fallback. */
    @Test
    fun `a recurring rule with no next firing is not overdue`() {
        assertFalse(recurring(1, nextFireAt = null).isOverdue(now))
    }

    /** `awaiting` stays authoritative: the server fired it and is waiting, repeating or not. */
    @Test
    fun `an awaiting recurring rule is overdue whatever its anchor says`() {
        val awaiting = task(
            1,
            dueAt = now.minusSeconds(60L * 60 * 24 * 60),
            status = TaskStatus.Awaiting,
            recurrence = "FREQ=DAILY",
            nextFireAt = now.plusSeconds(3600),
        )
        assertTrue(awaiting.isOverdue(now))
    }

    /**
     * Judged on one moment and sorted by another is the same bug wearing a
     * different hat: ordering on the anchor would pin every repeating task to
     * the top regardless of when it is next due.
     */
    @Test
    fun `ordering uses the firing for a recurring rule, not the anchor`() {
        val repeating = recurring(1, nextFireAt = now.minusSeconds(60))
        val oneShot = task(2, now.minusSeconds(7200))

        val content = TaskListContent(all = listOf(repeating, oneShot))

        assertEquals(listOf(2L, 1L), content.overdue(now).map { it.id })
    }
}
