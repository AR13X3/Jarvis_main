package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.PagedTodos
import com.ar13x.jarvis.core.model.Todo
import com.ar13x.jarvis.core.model.TodoEvents
import com.ar13x.jarvis.core.model.TodoPriority
import com.ar13x.jarvis.core.model.TodoStatus
import java.time.Instant

/**
 * To-dos (v2 plan §5). The same §4 seam as every other repository.
 *
 * **The backlog is not a method here.** §5.2 gives undated to-dos their own
 * place in the UI, and that place is `undated = true` over the one ordering —
 * a filter, not a different collection. A `backlog()` method would let the UI
 * drift into treating them as separate things, which is exactly what §5.2 says
 * they are not.
 */
interface TodoRepository {

    /**
     * [statuses] is a **set**, not a single value — unlike [TaskRepository.tasks],
     * where the gateway takes one. `GET /todos` takes a repeated `status` query
     * parameter, so the UI can honestly offer "open and doing" as one filter.
     */
    suspend fun todos(
        statuses: Set<TodoStatus> = emptySet(),
        tag: String? = null,
        undated: Boolean = false,
        priorities: Set<TodoPriority> = emptySet(),
        page: Int = 1,
    ): PagedTodos

    /**
     * One to-do's sub-tasks (§9.4.2).
     *
     * Separate from [todos] rather than another filter on it, because it is a
     * different question: [todos] answers "what is on my list", and the list
     * deliberately excludes children. This answers "what is inside this one".
     */
    suspend fun children(parentId: Long): List<Todo>

    /**
     * The activity trail (§9.4.3). Newest first.
     *
     * Read-only and paged. It is the only thing that can tell a to-do which was
     * touched-and-deferred from one that was genuinely forgotten — `updated_at`
     * cannot, and it is the field a reader reaches for first.
     */
    suspend fun history(todoId: Long, page: Int = 1): TodoEvents

    suspend fun todo(todoId: Long): Todo

    /** Direct, not proposed: a form cannot misparse "next Thursday" (§5.4). */
    suspend fun create(
        title: String,
        description: String? = null,
        startsAt: Instant? = null,
        dueAt: Instant? = null,
        tags: List<String> = emptyList(),
        priority: TodoPriority? = null,
        /** Creates it as a sub-task of that to-do. One level only. */
        parentId: Long? = null,
    ): Todo

    suspend fun setStatus(todoId: Long, status: TodoStatus): Todo

    /** Direct, like the star and the status chips — a tap cannot misparse (§5.4). */
    suspend fun setPriority(todoId: Long, priority: TodoPriority): Todo

    suspend fun setTitle(todoId: Long, title: String): Todo

    suspend fun setDescription(todoId: Long, description: String): Todo

    suspend fun setTags(todoId: Long, tags: List<String>): Todo

    /**
     * Sets or **clears** the deadline. `null` means clear, and it is a real,
     * ordinary edit rather than an edge case — it is how a dated to-do returns
     * to the backlog.
     *
     * Modelled as one method with a nullable argument rather than
     * `setDueAt` + `clearDueAt`, because the caller genuinely has one intent
     * ("the deadline is now this, or nothing"). The omit-versus-null distinction
     * the wire needs is handled below it, in `todoPatchBody`, where it belongs.
     */
    suspend fun setDueAt(todoId: Long, dueAt: Instant?): Todo

    // There is deliberately no `link`/`unlink` here any more. A to-do used to be
    // able to point at the reminders chasing it (§5.1, shipped in 395df72) and
    // Joy had it removed: "remove the reminder pointing thing, it is doing work
    // twice." Two mechanisms answered "when is this due" and both had to be kept
    // agreeing. `setDueAt` above is now the only answer.
}
