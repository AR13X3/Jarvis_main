package com.ar13x.jarvis.feature.tasks.list

import com.ar13x.jarvis.core.data.FakeBackend
import com.ar13x.jarvis.core.data.FakeTaskRepository
import com.ar13x.jarvis.core.model.TaskStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * The status filter, after `GET /tasks` grew a repeated `status` parameter.
 *
 * It took a single value when the chips were written, so they were single-select
 * and the comment on `TaskFilters` explained why a set would be dishonest. The
 * gateway grew the array and nothing on this side failed -- a capability the app
 * believes is missing is invisible, because nothing breaks. Found by probing the
 * served contract on 2026-09-02, a fortnight late.
 *
 * The empty case is the one worth pinning. An empty selection means *no filter*;
 * sending it as `status=[]` would be a filter matching nothing, and the screen
 * would look like an empty database rather than an unfiltered one.
 */
class StatusFilterTest {

    @Test
    fun `an empty selection is no filter, not a filter matching nothing`() = runBlocking {
        val repo = FakeTaskRepository(FakeBackend())

        val everything = repo.tasks(statuses = emptySet()).tasks

        assertTrue("an empty selection must not empty the list", everything.isNotEmpty())
    }

    @Test
    fun `several statuses are answered together`() = runBlocking {
        val repo = FakeTaskRepository(FakeBackend())
        val wanted = setOf(TaskStatus.Active, TaskStatus.Completed)

        val rows = repo.tasks(statuses = wanted).tasks

        assertTrue(rows.isNotEmpty())
        assertTrue(
            "every row must match one of the requested statuses",
            rows.all { it.status in wanted },
        )
        // ...and it is genuinely a union rather than one of the two being
        // ignored, which is what a naive "take the first status" would look
        // like: all rows would still match a requested status.
        //
        // Asserted by presence rather than by summing the two single-status
        // queries, because `tasks()` PAGES -- the union of 26 rows comes back
        // as one page of 20, and an arithmetic check would fail for a reason
        // that has nothing to do with filtering.
        assertTrue(rows.any { it.status == TaskStatus.Active })
        assertTrue(rows.any { it.status == TaskStatus.Completed })
    }

    @Test
    fun `filters are empty when nothing is selected`() {
        assertTrue(TaskFilters().isEmpty)
        assertFalse(TaskFilters(statuses = setOf(TaskStatus.Active)).isEmpty)
    }
}
