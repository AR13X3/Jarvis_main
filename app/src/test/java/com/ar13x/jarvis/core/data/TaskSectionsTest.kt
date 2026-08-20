package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.TaskStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The section queries, which are the *server's* queries (parent plan §2.7) and
 * live behind the repository seam so both implementations answer identically.
 *
 * These lock in the resolution of §5.2's open question — a task that is both
 * priority and recurring appears **once**, under Recurring, carrying its star.
 */
class TaskSectionsTest {

    private fun repository() = FakeTaskRepository(FakeBackend())

    @Test
    fun `a priority recurring task appears only under recurring, still starred`() = runTest {
        val sections = repository().sections()

        val weekly = sections.recurring.single { it.title == "Weekly review" }
        assertTrue("fixture must be both priority and recurring", weekly.isPriority)
        assertTrue(weekly.isRecurring)

        assertTrue(
            "a task shown under Recurring must not also be listed under Priority",
            sections.priority.none { it.id == weekly.id },
        )
    }

    /**
     * A repeating task that has fired is still a repeating task.
     *
     * Scoping Recurring to `active` put this one in Priority still showing
     * "Every Sunday", which reads exactly like the duplication the section split
     * was meant to remove. Being a rule does not stop because this week's
     * occurrence is waiting on you.
     */
    @Test
    fun `an awaiting recurring task stays under recurring`() = runTest {
        val sections = repository().sections()

        val bins = sections.recurring.single { it.title == "Take the bins out" }
        assertEquals(TaskStatus.Awaiting, bins.status)
        assertTrue(bins.isPriority)
        assertTrue(
            "no recurring task may appear under Priority",
            sections.priority.none { it.id == bins.id },
        )
    }

    @Test
    fun `priority never contains a recurring task`() = runTest {
        val sections = repository().sections()
        assertTrue(
            "recurring tasks belong under Recurring, whatever their status",
            sections.priority.none { it.isRecurring },
        )
    }

    @Test
    fun `no task is listed twice across priority and recurring`() = runTest {
        val sections = repository().sections()
        val overlap = sections.priority.map { it.id }.intersect(sections.recurring.map { it.id }.toSet())
        assertTrue("sections overlap on: " + overlap, overlap.isEmpty())
    }

    @Test
    fun `recurring lists every live repeating task, ordered by next fire`() = runTest {
        val sections = repository().sections()

        assertTrue(sections.recurring.all { it.isRecurring })
        // Terminal is the only thing that ends a rule: a cancelled or completed
        // repeating task has no next occurrence to show.
        assertTrue(sections.recurring.none { it.status.isTerminal })

        val fires = sections.recurring.map { it.nextFireAt ?: it.dueAt }
        assertEquals(fires.sorted(), fires)
    }

    @Test
    fun `all tasks is one page of twenty with more to come`() = runTest {
        val sections = repository().sections()
        assertEquals(20, sections.all.tasks.size)
        assertEquals(1, sections.all.page)
        assertTrue(sections.all.hasMore)
    }
}
