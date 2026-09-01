package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * A to-do (v2 plan §5). Fields mirror the served contract exactly
 * (sha256 `e398ff18e4aa6b33`).
 *
 * **A to-do's own deadline is the deadline** — and that reverses §5.1, on Joy's
 * instruction: *"remove the reminder pointing thing, it is doing work twice."*
 *
 * §5.1 had a to-do *own* tasks, so that a to-do could point at the reminders
 * chasing it and only one domain would own nagging. The argument was about not
 * building a second nagging engine, and it was a good one — but it produced two
 * mechanisms that both answered *when is this due*, which then had to be kept
 * agreeing with each other forever. Deleting the link does not build a second
 * engine: a to-do with a [dueAt] is simply **not chased**.
 *
 * So [dueAt] is still a date on a note rather than a promise to interrupt
 * anyone, and now nothing on a to-do interrupts at all. A thing that should
 * chase you is a *reminder*, made in the Tasks tab, which fires and extends and
 * lapses exactly as it always has. The two domains no longer refer to each other.
 *
 * `task_ids` is still served and is deliberately not decoded — `ignoreUnknownKeys`
 * drops it. gw03 is retiring `todos.todo_tasks` once this ships (tracker 131/132);
 * until then the app simply does not look, because a field that is read is a
 * field that can be believed.
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
