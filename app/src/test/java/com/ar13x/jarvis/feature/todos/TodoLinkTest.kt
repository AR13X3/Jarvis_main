package com.ar13x.jarvis.feature.todos

import com.ar13x.jarvis.core.data.FakeTodoRepository
import com.ar13x.jarvis.core.model.TodoStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Linking and unlinking a reminder (v2 plan §5.1).
 *
 * The link lives on the to-do side — `todos.todo_tasks`, not a `todo_id` column
 * on `tasks.tasks` — so that the domain that *works* does not depend on the
 * domain that is *new*. These assert the two consequences the UI depends on:
 * linking is idempotent, and **unlinking leaves the task alive**.
 */
class TodoLinkTest {

    @Test
    fun `linking is idempotent, as the route is`() = runBlocking {
        val repo = FakeTodoRepository()

        repo.link(todoId = 1, taskId = 31)
        val twice = repo.link(todoId = 1, taskId = 31)

        assertEquals(listOf(31L), twice.taskIds)
    }

    @Test
    fun `unlinking removes the link and nothing else`() = runBlocking {
        val repo = FakeTodoRepository()
        val before = repo.todo(2)
        assertTrue(before.hasReminders)

        val after = repo.unlink(todoId = 2, taskId = 31)

        // The to-do survives with everything else intact. The TASK surviving is
        // the gateway's half — "this reminder is not about that to-do" is not
        // "stop reminding me" — and the app must never word it as a deletion.
        assertFalse(after.hasReminders)
        assertEquals(before.title, after.title)
        assertEquals(before.status, after.status)
        assertEquals(before.dueAt, after.dueAt)
    }

    @Test
    fun `clearing a deadline leaves the reminders alone`() = runBlocking {
        // The two are independent on purpose: a to-do going back to the backlog
        // does not stop the reminders chasing it, and the detail screen says so
        // under the clear button.
        val repo = FakeTodoRepository()

        val cleared = repo.setDueAt(todoId = 2, dueAt = null)

        assertTrue(cleared.isUndated)
        assertTrue(cleared.hasReminders)
        assertEquals(listOf(31L), cleared.taskIds)
    }

    @Test
    fun `a cleared deadline puts the to-do into the backlog`() = runBlocking {
        // Not just a null field -- it has to show up under the backlog filter,
        // which is what §5.2 means by the backlog being a filter rather than a
        // place things are moved to.
        val repo = FakeTodoRepository()
        assertFalse(repo.todos(undated = true).todos.any { it.todoId == 2L })

        repo.setDueAt(todoId = 2, dueAt = null)

        assertTrue(repo.todos(undated = true).todos.any { it.todoId == 2L })
    }

    @Test
    fun `resolving a to-do does not touch its reminders`() = runBlocking {
        val repo = FakeTodoRepository()

        val done = repo.setStatus(todoId = 2, status = TodoStatus.Done)

        assertTrue(done.isResolved)
        assertTrue(done.hasReminders)
    }
}
