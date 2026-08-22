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
    ) = Task(
        id = id,
        title = "Task $id",
        description = "",
        dueAt = dueAt,
        dueDate = LocalDate.ofInstant(dueAt, sydney),
        isPriority = false,
        recurrence = recurrence,
        recurrenceText = if (recurrence != null) "Every Tuesday" else null,
        nextFireAt = dueAt,
        status = status,
        dueToday = true,
        createdAt = now,
        updatedAt = now,
    )

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
}
