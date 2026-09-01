package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.PagedTodos
import com.ar13x.jarvis.core.model.Todo
import com.ar13x.jarvis.core.model.TodoEvents
import com.ar13x.jarvis.core.model.TodoPriority
import com.ar13x.jarvis.core.model.TodoStatus
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * To-do fixtures, for building the screens off the tailnet and for tests.
 *
 * Drawn from the routine's own footer notes, because those are the real
 * un-placed work in Joy's week — "uni assessment weeks, 10-15h, not yet placed"
 * is exactly what an undated to-do is for, and it is already sitting in
 * `RoutineFixture.theWeek.notes` as prose nobody can act on.
 */
object TodoFixture {

    private val now: Instant = Instant.parse("2026-09-01T09:00:00Z")

    val all: List<Todo> = listOf(
        Todo(
            todoId = 1,
            title = "Place the uni assessment weeks",
            description = "10-15h. Wednesday's buffer is the only place it can come from.",
            status = TodoStatus.Open,
            tags = listOf("Uni"),
            createdAt = now.minusSeconds(60L * 60 * 24 * 18),
            updatedAt = now.minusSeconds(60L * 60 * 24 * 18),
        ),
        Todo(
            todoId = 2,
            title = "Off-machine backup target for replicate-backups.sh",
            description = "A disk failure currently loses the database and every backup of it.",
            status = TodoStatus.Doing,
            tags = listOf("CBAI"),
            // Dated. Nothing chases it — a to-do's deadline is a date on a
            // note, and the things that interrupt are reminders in Tasks.
            dueAt = now.plusSeconds(60L * 60 * 24 * 3),
            createdAt = now.minusSeconds(60L * 60 * 24 * 4),
            updatedAt = now.minusSeconds(60L * 60 * 2),
        ),
        Todo(
            todoId = 3,
            title = "Decide the tracked/scaffold split for every slot",
            description = "Guessed per slot today. It decides what the dashboard can say.",
            status = TodoStatus.Open,
            tags = listOf("Reskill"),
            createdAt = now.minusSeconds(60L * 60 * 24 * 20),
            updatedAt = now.minusSeconds(60L * 60 * 24 * 20),
        ),
        Todo(
            todoId = 4,
            title = "FCM measurement",
            status = TodoStatus.Done,
            tags = listOf("Reskill"),
            dueAt = now.minusSeconds(60L * 60 * 24 * 2),
            completedAt = now.minusSeconds(60L * 60 * 24 * 2),
            createdAt = now.minusSeconds(60L * 60 * 24 * 9),
            updatedAt = now.minusSeconds(60L * 60 * 24 * 2),
        ),
    )
}

/**
 * To-dos in memory.
 *
 * Nothing survives process death, deliberately — a fake that persisted would be
 * mistaken for a working feature, which is the same rule the routine fake keeps.
 *
 * It *does* implement the filters honestly rather than ignoring them, because
 * the backlog is a filter over one ordering (§5.2) and a fake that returned
 * everything regardless would let the UI be built against a distinction that
 * does not hold.
 */
@Singleton
class FakeTodoRepository @Inject constructor() : TodoRepository {

    private var rows: List<Todo> = TodoFixture.all

    override suspend fun todos(
        statuses: Set<TodoStatus>,
        tag: String?,
        undated: Boolean,
        priorities: Set<TodoPriority>,
        page: Int,
    ): PagedTodos = PagedTodos(
        todos = rows
            // Top-level only, matching the gateway's own default. A fake that
            // returned children as peers would let the list be built against a
            // shape the server never sends.
            .filter { !it.isSubTask }
            .filter { statuses.isEmpty() || it.status in statuses }
            .filter { tag == null || tag in it.tags }
            .filter { !undated || it.isUndated }
            .filter { priorities.isEmpty() || it.priority in priorities },
        page = 1,
        hasMore = false,
    )

    override suspend fun todo(todoId: Long): Todo =
        rows.first { it.todoId == todoId }

    override suspend fun create(
        title: String,
        description: String?,
        startsAt: Instant?,
        dueAt: Instant?,
        tags: List<String>,
        priority: TodoPriority?,
        parentId: Long?,
    ): Todo {
        val created = Todo(
            todoId = (rows.maxOfOrNull { it.todoId } ?: 0) + 1,
            title = title,
            description = description.orEmpty(),
            startsAt = startsAt,
            dueAt = dueAt,
            dueDate = dueAt?.atZone(ZoneId.systemDefault())?.toLocalDate(),
            tags = tags,
            priority = priority ?: TodoPriority.Normal,
            parentId = parentId,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        rows = rows + created
        // A new child changes its parent's rollup. The real gateway computes
        // this; here it has to be maintained by hand or a parent row would keep
        // saying "0 of 0" after a sub-task was added under it.
        parentId?.let { recount(it) }
        return created
    }

    override suspend fun children(parentId: Long): List<Todo> =
        rows.filter { it.parentId == parentId }

    /**
     * Empty rather than fabricated.
     *
     * The trail is append-only history the gateway keeps; inventing plausible
     * rows here would put fiction on a screen whose entire purpose is answering
     * "what actually happened to this".
     */
    override suspend fun history(todoId: Long, page: Int): TodoEvents = TodoEvents()

    override suspend fun setStatus(todoId: Long, status: TodoStatus): Todo =
        update(todoId) {
            it.copy(
                status = status,
                completedAt = if (status == TodoStatus.Done) Instant.now() else null,
                cancelledAt = if (status == TodoStatus.Cancelled) Instant.now() else null,
            )
        }.also { changed -> changed.parentId?.let { recount(it) } }

    override suspend fun setPriority(todoId: Long, priority: TodoPriority): Todo =
        update(todoId) { it.copy(priority = priority) }

    override suspend fun setTitle(todoId: Long, title: String): Todo =
        update(todoId) { it.copy(title = title) }

    override suspend fun setDescription(todoId: Long, description: String): Todo =
        update(todoId) { it.copy(description = description) }

    override suspend fun setTags(todoId: Long, tags: List<String>): Todo =
        update(todoId) { it.copy(tags = tags) }

    /**
     * Stands in for a gateway **in the same timezone as the phone**, which is
     * the arrangement that actually holds today.
     *
     * It used to answer `dueDate = null`, which was honest about the fake not
     * being a server and made the detail screen say "No deadline" straight after
     * one had been set. Deriving the day here is the §3.2 conversion the *app*
     * must never do — but this is standing in for the side that is supposed to
     * do it, so doing it is the point rather than the mistake.
     */
    override suspend fun setDueAt(todoId: Long, dueAt: Instant?): Todo =
        update(todoId) {
            it.copy(
                dueAt = dueAt,
                dueDate = dueAt?.atZone(ZoneId.systemDefault())?.toLocalDate(),
            )
        }

    /** Recomputes one parent's `child_count` / `child_done`, as the gateway does. */
    private fun recount(parentId: Long) {
        val kids = rows.filter { it.parentId == parentId }
        rows = rows.map {
            if (it.todoId != parentId) {
                it
            } else {
                it.copy(
                    childCount = kids.size,
                    childDone = kids.count { kid -> kid.status == TodoStatus.Done },
                )
            }
        }
    }

    private fun update(todoId: Long, change: (Todo) -> Todo): Todo {
        val updated = change(rows.first { it.todoId == todoId }).copy(updatedAt = Instant.now())
        rows = rows.map { if (it.todoId == todoId) updated else it }
        return updated
    }
}
