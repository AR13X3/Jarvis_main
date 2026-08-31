package com.ar13x.jarvis.feature.tasks.list

import androidx.compose.runtime.Immutable
import com.ar13x.jarvis.core.model.CancelScope
import com.ar13x.jarvis.core.model.FailureReason
import com.ar13x.jarvis.core.model.SessionSummary
import com.ar13x.jarvis.core.model.Task
import java.time.Instant
import com.ar13x.jarvis.core.model.TaskStatus
import com.ar13x.jarvis.core.ui.LoadState
import java.time.LocalDate
import java.time.ZoneId

/**
 * One state class for the screen, one sealed interface of events upward
 * (plan §8.1). Loading and failure are modelled as states rather than booleans,
 * so "loading and failed at the same time" is not expressible.
 */
@Immutable
data class TaskListUiState(
    val content: LoadState<TaskListContent> = LoadState.Loading,
    val filters: TaskFilters = TaskFilters(),
    /** Appending the next page of All tasks. Distinct from the initial load. */
    val appending: Boolean = false,
    val appendFailure: FailureReason? = null,
    /** Non-null while the cancel confirmation dialog is up. */
    val pendingCancel: Task? = null,
    val cancelInFlight: Boolean = false,
    /** Drives the undo snackbar after a priority toggle. */
    val undo: UndoPriority? = null,
    val transientFailure: FailureReason? = null,

    /**
     * Task conversations that never became tasks.
     *
     * Unbound sessions with messages in them. Nothing in the task list points
     * at these — there is no task yet — so before this they were unreachable
     * the moment you backed out, despite still existing on the server.
     */
    val drafts: List<SessionSummary> = emptyList(),
    /**
     * Which rows are showing their description.
     *
     * Held here rather than inside the row so it survives the row scrolling out
     * of the composition — a card that silently collapsed itself because you
     * scrolled past it and back would read as a bug.
     */
    val expandedIds: Set<Long> = emptySet(),
)

@Immutable
data class TaskListContent(
    /** `is_priority && status in (active, awaiting)`, by `due_at`. */
    val priority: List<Task> = emptyList(),
    /** `recurrence != null && status == active`, by `next_fire_at`. */
    val recurring: List<Task> = emptyList(),
    /** Everything, newest first, paged 20. */
    val all: List<Task> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
) {
    /**
     * Past their moment and still open — the section that goes above everything
     * else, because it is the only one that is already costing you something.
     *
     * Two kinds land here:
     *
     * - **`awaiting`** — the server's own word for "fired, waiting on you"
     *   (parent plan §2.8). This is the authoritative one.
     * - **`active` with its moment in the past** — the gap between a deadline
     *   passing and the scheduler noticing. Without this the task you set for
     *   6:40 sits in the list looking perfectly fine at 6:45. Which moment that
     *   is differs for a repeating task; see [Task.overdueMoment].
     *
     * `incomplete` is deliberately **not** here. It has already lapsed; it is a
     * record rather than something demanding an answer, and mixing the two
     * would make the section something you learn to ignore.
     *
     * **This compares two instants**, which is timezone-independent, and it is
     * not what §3.2 forbids — that rule is about deriving a calendar *day* from
     * a timestamp, which the server still does. Nothing here re-derives
     * `due_date` or `due_today`.
     *
     * Derived from the tasks already loaded rather than from a server section,
     * so it can only see what has been paged in. Everything in `priority` and
     * `recurring` arrives whole, and page one is the rest — an overdue task
     * buried on page four is missed until it is paged in. The complete fix is
     * an `overdue` array on `/tasks/sections`; see `docs/joy-to-gw03-07`.
     */
    fun overdue(now: Instant = Instant.now()): List<Task> =
        (priority + recurring + all)
            .distinctBy { it.id }
            .filter { it.isOverdue(now) }
            .sortedBy { it.overdueMoment ?: it.dueAt }

    /**
     * Priority and Recurring are unfiltered views by definition — they *are*
     * filters. Showing them beside a filtered All-tasks list produces a screen
     * that contradicts itself: filter to "Completed" and the Priority section
     * still shows active tasks.
     *
     * So when a filter is on, the sections collapse to the single result list.
     * This is a judgement call the plan does not make explicitly — see
     * BUILD_NOTES §7.
     */
    fun showsSections(filters: TaskFilters): Boolean = filters.isEmpty
}

/**
 * Past due and still open. See [TaskListContent.overdue] for why this compares
 * instants and why that is not the §3.2 violation it resembles.
 *
 * **Which moment counts depends on whether the task repeats.** For a one-shot
 * task it is [Task.dueAt]. For a recurring one `due_at` is the series *anchor*
 * and never moves — the rule stays `active` between firings by design (parent
 * §2.5 rule 1) — so comparing it to now marks every repeating task overdue
 * forever. A daily task created in June would sit here for the rest of its
 * life. [Task.nextFireAt] is the moment that actually has a deadline.
 *
 * A recurring task with no `next_fire_at` is not overdue: there is no next
 * moment to have missed, and the anchor is the wrong thing to fall back to.
 *
 * This mirrors the server, which asks whether a live *occurrence* has passed
 * rather than reading the parent's `due_at` — see `docs/gw03-to-joy-08` §4.3.
 * When the `overdue` array on `/tasks/sections` lands this whole derivation is
 * replaced by it.
 */
fun Task.isOverdue(now: Instant = Instant.now()): Boolean = when (status) {
    TaskStatus.Awaiting -> true
    TaskStatus.Active -> overdueMoment?.isBefore(now) == true
    else -> false
}

/**
 * The moment this task is measured against: its next firing if it repeats, its
 * deadline if it does not. Null for a recurring rule with nothing scheduled.
 *
 * Both [Task.isOverdue] and the section's ordering read this, so a repeating
 * task cannot be judged by one moment and sorted by another — sorting on the
 * anchor would pin every recurring task to the top of the list regardless of
 * when it is actually next due.
 */
val Task.overdueMoment: Instant?
    get() = if (isRecurring) nextFireAt else dueAt

@Immutable
data class TaskFilters(
    /**
     * One status at a time. `GET /tasks` accepts a single `status`, so a
     * multi-select chip row would offer a combination the gateway cannot
     * answer — the filter would silently mean something other than it showed.
     */
    val status: TaskStatus? = null,
    val range: DateRange = DateRange.Any,
) {
    val isEmpty: Boolean get() = status == null && range == DateRange.Any

    /**
     * Resolves the range to the bare calendar days the API takes.
     *
     * Computing "today" here is **not** the thing §3.2 forbids. That rule is
     * about deriving a *task's* display day from its instant, where the server's
     * UTC day and the user's local day disagree. This is the opposite direction:
     * the user is asking "show me things due today", and today is a property of
     * where the user is standing, not of any task.
     */
    fun bounds(zone: ZoneId = ZoneId.systemDefault()): Pair<LocalDate?, LocalDate?> {
        val today = LocalDate.now(zone)
        return when (range) {
            DateRange.Any -> null to null
            DateRange.Today -> today to today
            DateRange.Week -> today to today.plusDays(6)
            DateRange.Overdue -> null to today.minusDays(1)
        }
    }
}

enum class DateRange(val label: String) {
    Any("Any time"),
    Today("Today"),
    Week("Next 7 days"),
    Overdue("Overdue"),
}

/**
 * The undo that makes "no confirmation" defensible (plan §5.2).
 *
 * Direct manipulation of a cheap, reversible flag does not confirm — but that is
 * only true if reversing it is genuinely one tap away.
 */
@Immutable
data class UndoPriority(
    val taskId: Long,
    val title: String,
    /** What it was *before*, which is what undo restores. */
    val previous: Boolean,
)

sealed interface TaskListEvent {
    data object Refresh : TaskListEvent
    data object LoadMore : TaskListEvent
    data object RetryAppend : TaskListEvent

    data class TogglePriority(val task: Task) : TaskListEvent
    data object Undo : TaskListEvent
    data object DismissUndo : TaskListEvent

    data class RequestCancel(val task: Task) : TaskListEvent
    data class ConfirmCancel(val scope: CancelScope) : TaskListEvent
    data object DismissCancel : TaskListEvent

    data class ToggleExpand(val taskId: Long) : TaskListEvent

    /**
     * Ticking a checklist item inside a description (§5.4: direct manipulation
     * of something cheap and reversible does not confirm).
     */
    data class ToggleChecklistItem(val task: Task, val line: Int) : TaskListEvent

    /** Re-enter an unfinished task conversation. */
    data class OpenDraft(val sessionId: String) : TaskListEvent

    data class ToggleStatusFilter(val status: TaskStatus) : TaskListEvent
    data class SetRange(val range: DateRange) : TaskListEvent
    data object ClearFilters : TaskListEvent

    data object DismissFailure : TaskListEvent
}
