package com.ar13x.jarvis.feature.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.core.data.TodoRepository
import com.ar13x.jarvis.core.data.toFailureReason
import com.ar13x.jarvis.core.model.FailureReason
import com.ar13x.jarvis.core.model.Todo
import com.ar13x.jarvis.core.model.TodoStatus
import com.ar13x.jarvis.core.ui.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * What the list is currently asking for.
 *
 * [undatedOnly] is **the backlog** — a filter over the one ordering, not a
 * separate collection (§5.2). Keeping it here rather than in a second screen is
 * what stops the two drifting into different sort orders and different rows.
 */
data class TodoFilters(
    val statuses: Set<TodoStatus> = setOf(TodoStatus.Open, TodoStatus.Doing),
    val tag: String? = null,
    val undatedOnly: Boolean = false,
)

data class TodoListUiState(
    val content: LoadState<List<Todo>> = LoadState.Loading,
    val filters: TodoFilters = TodoFilters(),
    /** Tags seen in the current results — the filter row is built from the data. */
    val tags: List<String> = emptyList(),
    /** A write that failed. Shown and dismissed; never queued (plan §3.5). */
    val transientFailure: FailureReason? = null,
)

@HiltViewModel
class TodoListViewModel @Inject constructor(
    private val repository: TodoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TodoListUiState())
    val state: StateFlow<TodoListUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(content = LoadState.Loading) }
            val filters = _state.value.filters
            try {
                val page = repository.todos(
                    statuses = filters.statuses,
                    tag = filters.tag,
                    undated = filters.undatedOnly,
                )
                _state.update { current ->
                    current.copy(
                        content = LoadState.Ready(page.todos),
                        // Only ever grows within a session. A tag chip that
                        // vanished because the current filter excluded the last
                        // to-do carrying it would take away the only way back.
                        tags = (current.tags + page.todos.flatMap { it.tags }).distinct().sorted(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(content = LoadState.Failed(e.toFailureReason())) }
            }
        }
    }

    fun toggleStatus(status: TodoStatus) = applyFilters { filters ->
        filters.copy(
            statuses = if (status in filters.statuses) {
                filters.statuses - status
            } else {
                filters.statuses + status
            },
        )
    }

    fun toggleTag(tag: String) = applyFilters { filters ->
        filters.copy(tag = if (filters.tag == tag) null else tag)
    }

    fun toggleBacklog() = applyFilters { it.copy(undatedOnly = !it.undatedOnly) }

    /**
     * Advances a to-do one step, the way a tap should.
     *
     * `open → doing → done`, and `done` back to `open` so a mis-tap is undone by
     * tapping again — the same argument that lets the priority toggle write
     * without a confirmation (§5.4). `cancelled` is deliberately not reachable
     * from here: it is a decision rather than a step, and it belongs on the
     * detail screen where it can be deliberate.
     */
    fun advance(todo: Todo) {
        val next = when (todo.status) {
            TodoStatus.Open -> TodoStatus.Doing
            TodoStatus.Doing -> TodoStatus.Done
            TodoStatus.Done -> TodoStatus.Open
            TodoStatus.Cancelled -> TodoStatus.Open
        }
        viewModelScope.launch {
            try {
                val updated = repository.setStatus(todo.todoId, next)
                _state.update { current ->
                    current.copy(
                        content = current.content.mapList { rows ->
                            rows.map { if (it.todoId == updated.todoId) updated else it }
                        },
                    )
                }
            } catch (e: Exception) {
                // The row is left as it was. There is no optimistic update to
                // roll back, deliberately: the gateway owns the state, and a
                // row that flipped and then flipped back is harder to read than
                // one that never moved.
                _state.update { it.copy(transientFailure = e.toFailureReason()) }
            }
        }
    }

    /**
     * Creates and shows it immediately, without a round trip to reload the list.
     *
     * Prepended rather than re-fetched: the new to-do is the server's own
     * response, so this is not an optimistic guess -- it is the created row,
     * placed where the person who just typed it will look. A full reload would
     * also drop it straight back out of view whenever the current filter
     * excludes it, which is the wrong feedback for "I just added that".
     */
    fun create(
        title: String,
        description: String,
        tags: List<String>,
        dueAt: Instant? = null,
    ) {
        viewModelScope.launch {
            try {
                val created = repository.create(
                    title = title,
                    description = description.takeIf { it.isNotBlank() },
                    // Undated stays the ordinary case (§5.2): this is `null`
                    // unless the capture sheet's optional row was actually used.
                    dueAt = dueAt,
                    tags = tags,
                )
                _state.update { current ->
                    current.copy(
                        content = current.content.mapList { listOf(created) + it },
                        tags = (current.tags + created.tags).distinct().sorted(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(transientFailure = e.toFailureReason()) }
            }
        }
    }

    fun dismissFailure() = _state.update { it.copy(transientFailure = null) }

    private fun applyFilters(change: (TodoFilters) -> TodoFilters) {
        _state.update { it.copy(filters = change(it.filters)) }
        load()
    }
}

/** Maps the list inside a [LoadState] without disturbing loading or failure. */
private inline fun LoadState<List<Todo>>.mapList(
    transform: (List<Todo>) -> List<Todo>,
): LoadState<List<Todo>> = when (this) {
    is LoadState.Ready -> LoadState.Ready(transform(data))
    else -> this
}
