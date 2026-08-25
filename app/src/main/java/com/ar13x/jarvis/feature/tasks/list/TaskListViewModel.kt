package com.ar13x.jarvis.feature.tasks.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.core.data.TaskRepository
import com.ar13x.jarvis.core.model.CancelScope
import com.ar13x.jarvis.core.data.toFailureReason
import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.ui.toggleChecklistItem
import com.ar13x.jarvis.core.ui.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val tasks: TaskRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TaskListUiState())
    val state: StateFlow<TaskListUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var appendJob: Job? = null

    init {
        load()
    }

    fun onEvent(event: TaskListEvent) {
        when (event) {
            TaskListEvent.Refresh -> load()
            TaskListEvent.LoadMore -> loadMore()
            TaskListEvent.RetryAppend -> loadMore(force = true)

            is TaskListEvent.TogglePriority -> togglePriority(event.task)
            TaskListEvent.Undo -> undoPriority()
            TaskListEvent.DismissUndo -> _state.update { it.copy(undo = null) }

            is TaskListEvent.RequestCancel -> _state.update { it.copy(pendingCancel = event.task) }
            TaskListEvent.DismissCancel -> _state.update { it.copy(pendingCancel = null) }
            is TaskListEvent.ConfirmCancel -> confirmCancel(event.scope)

            is TaskListEvent.ToggleStatusFilter -> {
                val current = _state.value.filters
                // Tapping the selected chip clears it, so the row still behaves
                // like a toggle even though only one can be on.
                val next = if (current.status == event.status) null else event.status
                applyFilters(current.copy(status = next))
            }
            is TaskListEvent.SetRange -> applyFilters(_state.value.filters.copy(range = event.range))
            TaskListEvent.ClearFilters -> applyFilters(TaskFilters())

            is TaskListEvent.ToggleChecklistItem -> toggleChecklistItem(event.task, event.line)

            is TaskListEvent.ToggleExpand -> _state.update { state ->
                val next = if (event.taskId in state.expandedIds) {
                    state.expandedIds - event.taskId
                } else {
                    state.expandedIds + event.taskId
                }
                state.copy(expandedIds = next)
            }

            TaskListEvent.DismissFailure -> _state.update { it.copy(transientFailure = null) }
        }
    }

    private fun applyFilters(filters: TaskFilters) {
        _state.update { it.copy(filters = filters) }
        // A filter change genuinely invalidates what is on screen, so this one
        // does show the loading state rather than leaving the old answer up.
        load(showLoading = true)
    }

    /**
     * Unfiltered, this is a **single round trip** — `/tasks/sections` exists
     * precisely so the whole tab's first paint costs one request (plan §4.4).
     * Once a filter is on, the sections no longer apply and the screen becomes
     * one query against `/tasks`.
     */
    /**
     * @param showLoading blank the list while fetching. False for a plain
     *   refresh: switching tabs re-reads on resume, and dropping to a spinner
     *   every time made returning to Tasks look like the app was reloading
     *   itself. The old rows stay up and are replaced when the new ones land —
     *   they were correct a moment ago, and almost always still are.
     */
    private fun load(showLoading: Boolean = _state.value.content !is LoadState.Ready) {
        loadJob?.cancel()
        appendJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    content = if (showLoading) LoadState.Loading else it.content,
                    appendFailure = null,
                )
            }
            val filters = _state.value.filters
            runCatching {
                if (filters.isEmpty) {
                    val sections = tasks.sections()
                    TaskListContent(
                        priority = sections.priority,
                        recurring = sections.recurring,
                        all = sections.all.tasks,
                        page = sections.all.page,
                        hasMore = sections.all.hasMore,
                    )
                } else {
                    val (from, to) = filters.bounds()
                    val page = tasks.tasks(filters.status, from, to, page = 1)
                    TaskListContent(all = page.tasks, page = page.page, hasMore = page.hasMore)
                }
            }.onSuccess { content ->
                _state.update { it.copy(content = LoadState.Ready(content)) }
            }.onFailure { error ->
                _state.update { state ->
                    // A failed background refresh keeps the rows it already had.
                    // Replacing a working list with an error because one poll
                    // failed is worse than showing slightly old data.
                    if (state.content is LoadState.Ready) {
                        state.copy(transientFailure = error.toFailureReason())
                    } else {
                        state.copy(content = LoadState.Failed(error.toFailureReason()))
                    }
                }
            }
        }
    }

    private fun loadMore(force: Boolean = false) {
        val current = _state.value
        val content = current.content.dataOrNull ?: return
        if (!content.hasMore) return
        if (current.appending) return
        if (current.appendFailure != null && !force) return
        if (appendJob?.isActive == true) return

        appendJob = viewModelScope.launch {
            _state.update { it.copy(appending = true, appendFailure = null) }
            val filters = current.filters
            val (from, to) = filters.bounds()
            runCatching { tasks.tasks(filters.status, from, to, page = content.page + 1) }
                .onSuccess { page ->
                    _state.update { state ->
                        val existing = state.content.dataOrNull ?: return@update state
                        // Guard against a duplicate id arriving twice: the list is
                        // keyed by id in the UI, and Compose crashes outright on a
                        // duplicate key rather than degrading.
                        val seen = existing.all.mapTo(mutableSetOf()) { it.id }
                        val merged = existing.all + page.tasks.filter { it.id !in seen }
                        state.copy(
                            appending = false,
                            content = LoadState.Ready(
                                existing.copy(all = merged, page = page.page, hasMore = page.hasMore),
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(appending = false, appendFailure = error.toFailureReason())
                    }
                }
        }
    }

    /**
     * The one deliberate exception to "never write without a proposal" (§3.4).
     *
     * The flag flips locally at once so the menu and the row agree with the tap,
     * then the authoritative refresh moves the row between sections — which is
     * the list mutation §6.5 wants you to *see*. This is not the offline write
     * queue §3.5 forbids: nothing is stored, and a failure reverts immediately
     * rather than being retried later against a task the agent may have moved on.
     */
    private fun togglePriority(task: Task) {
        val next = !task.isPriority
        _state.update { state ->
            state.copy(
                content = state.content.mapContent { it.withPriority(task.id, next) },
                undo = UndoPriority(task.id, task.title, previous = task.isPriority),
            )
        }
        viewModelScope.launch {
            runCatching { tasks.setPriority(task.id, next) }
                .onSuccess { reload() }
                .onFailure { error ->
                    _state.update { state ->
                        state.copy(
                            content = state.content.mapContent { it.withPriority(task.id, task.isPriority) },
                            undo = null,
                            transientFailure = error.toFailureReason(),
                        )
                    }
                }
        }
    }

    /**
     * Ticks a checklist item, optimistically.
     *
     * Same shape as [togglePriority] and for the same reason: the tap is
     * instant, the round trip is not, and a checkbox that waits ~4s to move
     * feels broken. On failure the description is put back exactly as it was and
     * the reason is shown — there is no queue (§3.5).
     *
     * No undo snackbar, unlike priority. Ticking again *is* the undo, it is
     * right there under the thumb, and a snackbar for every tick in a ten-item
     * list would be its own kind of noise.
     */
    private fun toggleChecklistItem(task: Task, line: Int) {
        val updated = task.description.toggleChecklistItem(line)
        // Unchanged means a stale index — the agent rewrote the description
        // between this list being drawn and the tap landing. Doing nothing is
        // correct; the next reload will show what it actually says now.
        if (updated == task.description) return

        _state.update { state ->
            state.copy(content = state.content.mapContent { it.withDescription(task.id, updated) })
        }

        viewModelScope.launch {
            runCatching { tasks.updateDescription(task.id, updated) }
                .onSuccess { reload() }
                .onFailure { error ->
                    _state.update { state ->
                        state.copy(
                            content = state.content.mapContent {
                                it.withDescription(task.id, task.description)
                            },
                            transientFailure = error.toFailureReason(),
                        )
                    }
                }
        }
    }

    private fun undoPriority() {
        val undo = _state.value.undo ?: return
        _state.update { state ->
            state.copy(
                content = state.content.mapContent { it.withPriority(undo.taskId, undo.previous) },
                undo = null,
            )
        }
        viewModelScope.launch {
            runCatching { tasks.setPriority(undo.taskId, undo.previous) }
                .onSuccess { reload() }
                .onFailure { error ->
                    _state.update { it.copy(transientFailure = error.toFailureReason()) }
                }
        }
    }

    /**
     * Cancel is confirmed from either path because it is not cheaply reversible
     * (parent plan §2.7). The row is **not** removed — rows are never deleted;
     * it persists with `status = cancelled` (plan §5.2).
     */
    private fun confirmCancel(scope: CancelScope) {
        val target = _state.value.pendingCancel ?: return
        viewModelScope.launch {
            _state.update { it.copy(cancelInFlight = true) }
            runCatching { tasks.cancel(target.id, scope) }
                .onSuccess {
                    _state.update { it.copy(cancelInFlight = false, pendingCancel = null) }
                    reload()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            cancelInFlight = false,
                            pendingCancel = null,
                            transientFailure = error.toFailureReason(),
                        )
                    }
                }
        }
    }

    /**
     * Re-fetches everything currently on screen, including pages the user has
     * already scrolled to.
     *
     * Refreshing only page 1 would silently truncate a list somebody had scrolled
     * halfway through, which reads as data loss rather than as a refresh.
     */
    private suspend fun reload() {
        val current = _state.value
        val pagesLoaded = current.content.dataOrNull?.page ?: 1
        val filters = current.filters

        runCatching {
            if (filters.isEmpty) {
                val sections = tasks.sections()
                var all = sections.all.tasks
                var page = sections.all.page
                var hasMore = sections.all.hasMore
                while (page < pagesLoaded && hasMore) {
                    val next = tasks.tasks(page = page + 1)
                    all = all + next.tasks
                    page = next.page
                    hasMore = next.hasMore
                }
                TaskListContent(sections.priority, sections.recurring, all, page, hasMore)
            } else {
                val (from, to) = filters.bounds()
                var all = emptyList<Task>()
                var page = 0
                var hasMore = true
                while (page < pagesLoaded && hasMore) {
                    val next = tasks.tasks(filters.status, from, to, page = page + 1)
                    all = all + next.tasks
                    page = next.page
                    hasMore = next.hasMore
                }
                TaskListContent(all = all, page = page, hasMore = hasMore)
            }
        }.onSuccess { content ->
            _state.update { it.copy(content = LoadState.Ready(content)) }
        }.onFailure { error ->
            _state.update { it.copy(transientFailure = error.toFailureReason()) }
        }
    }
}

private fun LoadState<TaskListContent>.mapContent(
    transform: (TaskListContent) -> TaskListContent,
): LoadState<TaskListContent> =
    if (this is LoadState.Ready) LoadState.Ready(transform(data)) else this

private fun TaskListContent.withPriority(taskId: Long, isPriority: Boolean): TaskListContent {
    fun List<Task>.patch() = map { if (it.id == taskId) it.copy(isPriority = isPriority) else it }
    return copy(priority = priority.patch(), recurring = recurring.patch(), all = all.patch())
}

/**
 * The same optimistic patch as [withPriority], for a ticked checklist item.
 *
 * A task appears in more than one section, so all three have to be patched or
 * the same task shows ticked in Overdue and unticked in All.
 */
private fun TaskListContent.withDescription(taskId: Long, description: String): TaskListContent {
    fun List<Task>.patch() = map { if (it.id == taskId) it.copy(description = description) else it }
    return copy(priority = priority.patch(), recurring = recurring.patch(), all = all.patch())
}
