package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.PagedTodos
import com.ar13x.jarvis.core.model.Todo
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
        page: Int = 1,
    ): PagedTodos

    suspend fun todo(todoId: Long): Todo

    /** Direct, not proposed: a form cannot misparse "next Thursday" (§5.4). */
    suspend fun create(
        title: String,
        description: String? = null,
        startsAt: Instant? = null,
        dueAt: Instant? = null,
        tags: List<String> = emptyList(),
    ): Todo

    suspend fun setStatus(todoId: Long, status: TodoStatus): Todo

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

    /**
     * Points an existing reminder at this to-do (§5.1).
     *
     * A task, not a copy. Setting a reminder on a to-do means creating the task
     * through the flow that already fires, chases and extends, then pointing at
     * it — which is why there is no "add reminder" that invents one here.
     */
    suspend fun link(todoId: Long, taskId: Long): Todo

    /** Detaches a reminder. **The task survives and keeps firing.** */
    suspend fun unlink(todoId: Long, taskId: Long): Todo
}
