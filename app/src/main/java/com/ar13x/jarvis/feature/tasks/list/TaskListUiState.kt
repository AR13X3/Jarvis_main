package com.ar13x.jarvis.feature.tasks.list

import androidx.compose.runtime.Immutable
import com.ar13x.jarvis.core.model.CancelScope
import com.ar13x.jarvis.core.model.FailureReason
import com.ar13x.jarvis.core.model.Task
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

    data class ToggleStatusFilter(val status: TaskStatus) : TaskListEvent
    data class SetRange(val range: DateRange) : TaskListEvent
    data object ClearFilters : TaskListEvent

    data object DismissFailure : TaskListEvent
}
