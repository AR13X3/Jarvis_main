package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * A to-do (v2 plan §5). Fields mirror the served contract exactly
 * (sha256 `e398ff18e4aa6b33`).
 *
 * **A to-do *has* tasks; it is not one** (§5.1). It carries no deadline
 * machinery of its own — no firing, no chasing, no extensions — because if two
 * systems chase the same deadline the user gets nagged twice and neither knows
 * the other stopped. Setting a reminder on a to-do means creating a *task*
 * through the flow that already fires and chases, then pointing at it.
 *
 * So [dueAt] here is a date on a note, not a promise to interrupt anyone. The
 * things that interrupt are in [taskIds].
 *
 * Note it is nullable, unlike [Task.dueAt]. An undated to-do is legal and
 * ordinary — it is the backlog — and `todos.due_at` was nullable from birth in
 * migration 0009. `tasks.due_at` was never touched and stays non-null; the plan
 * once said otherwise and that stale paragraph is corrected in §5.2.
 */
@Serializable
data class Todo(
    @SerialName("todo_id") val todoId: Long,
    val title: String,
    val description: String = "",

    @Serializable(InstantSerializer::class)
    @SerialName("starts_at") val startsAt: Instant? = null,
    @Serializable(InstantSerializer::class)
    @SerialName("due_at") val dueAt: Instant? = null,

    /**
     * The **local** calendar days, computed by the server.
     *
     * Never derived from [startsAt] / [dueAt] here. The server runs UTC, so a
     * 9pm Sydney deadline falls on the next UTC day — §3.2's highest-risk defect
     * and invisible until it bites.
     */
    @Serializable(LocalDateSerializer::class)
    @SerialName("starts_on") val startsOn: LocalDate? = null,
    @Serializable(LocalDateSerializer::class)
    @SerialName("due_date") val dueDate: LocalDate? = null,

    val status: TodoStatus = TodoStatus.Open,
    val tags: List<String> = emptyList(),

    /**
     * The reminders chasing this to-do, if any.
     *
     * The link lives on the to-do side (`todos.todo_tasks`), not as a `todo_id`
     * column on `tasks.tasks` — deliberately, so the domain that *works*
     * does not depend on the domain that is *new*. Drop the to-dos schema
     * entirely and every reminder keeps firing.
     */
    @SerialName("task_ids") val taskIds: List<Long> = emptyList(),

    @Serializable(InstantSerializer::class)
    @SerialName("created_at") val createdAt: Instant,
    @Serializable(InstantSerializer::class)
    @SerialName("updated_at") val updatedAt: Instant,
    @Serializable(InstantSerializer::class)
    @SerialName("completed_at") val completedAt: Instant? = null,
    @Serializable(InstantSerializer::class)
    @SerialName("cancelled_at") val cancelledAt: Instant? = null,
) {
    /** Undated. This is what the backlog is made of (§5.2). */
    val isUndated: Boolean get() = dueAt == null

    /** Nothing more will happen to it. Mirrors [TaskStatus.isTerminal] in spirit. */
    val isResolved: Boolean get() = status == TodoStatus.Done || status == TodoStatus.Cancelled

    /** Has reminders pointing at it — the things that will actually interrupt. */
    val hasReminders: Boolean get() = taskIds.isNotEmpty()
}

@Serializable
data class PagedTodos(
    val todos: List<Todo> = emptyList(),
    val page: Int = 1,
    @SerialName("has_more") val hasMore: Boolean = false,
)

/**
 * `POST`, `PATCH` and the link routes all answer with the to-do they changed.
 *
 * Named in the schema rather than returned as a bare object, which is what lets
 * the app drop the tolerant envelope-or-object decoder it needed for tasks.
 */
@Serializable
data class TodoEnvelope(val todo: Todo)
