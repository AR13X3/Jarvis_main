package com.ar13x.jarvis.feature.todos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.core.data.TaskRepository
import com.ar13x.jarvis.core.data.TodoRepository
import com.ar13x.jarvis.core.data.toFailureReason
import com.ar13x.jarvis.core.model.FailureReason
import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.model.TaskStatus
import com.ar13x.jarvis.core.model.Todo
import com.ar13x.jarvis.core.model.TodoStatus
import com.ar13x.jarvis.core.ui.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class TodoDetailUiState(
    val todo: LoadState<Todo> = LoadState.Loading,
    val transientFailure: FailureReason? = null,
    /**
     * Set when the day the user picked and the day the server filed it under
     * are not the same one. Almost always `null`.
     *
     * **This is a tripwire, not a feature.** The app sends an instant and the
     * gateway derives the calendar day from it in its own configured zone
     * (§3.2); if that zone ever stops matching the phone's, every deadline
     * quietly lands a day out with the time still perfectly correct, which reads
     * as bad data rather than as a bug. This project has paid for that class of
     * mistake more than once, and it is invisible precisely because nothing
     * fails. Fifteen lines to make it announce itself is cheap.
     */
    val deadlineDayMismatch: DeadlineDayMismatch? = null,
)

/** The day asked for, and the day the gateway actually stored it under. */
data class DeadlineDayMismatch(val asked: LocalDate, val stored: LocalDate)

/**
 * One to-do.
 *
 * Every edit here is direct, with no proposal, and that is §5.4's rule rather
 * than an exception to it: the model confirms because it can misparse "next
 * Thursday"; a tap on a status or a deadline cannot.
 *
 * It no longer holds a [TaskRepository]. It used to, only to resolve the
 * reminders a to-do pointed at — that link is deleted (§9.5), and with it the
 * one place the to-do domain reached into the task domain.
 */
@HiltViewModel
class TodoDetailViewModel @Inject constructor(
    private val todos: TodoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val todoId: Long = checkNotNull(savedStateHandle["todoId"])

    private val _state = MutableStateFlow(TodoDetailUiState())
    val state: StateFlow<TodoDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(todo = LoadState.Loading) }
            try {
                apply(todos.todo(todoId))
            } catch (e: Exception) {
                _state.update { it.copy(todo = LoadState.Failed(e.toFailureReason())) }
            }
        }
    }

    fun setStatus(status: TodoStatus) = mutate { todos.setStatus(todoId, status) }

    /**
     * `null` clears the deadline, and that is an ordinary edit: it is how a
     * dated to-do goes back to the backlog (§5.2).
     *
     * The distinction between "clear it" and "leave it alone" is carried all the
     * way to the wire by `todoPatchBody` — a nullable field on a data class
     * could not have expressed it, because `JarvisJson` omits nulls.
     */
    fun setDueAt(dueAt: Instant?) = mutate { todos.setDueAt(todoId, dueAt) }

    /**
     * Sets the deadline to an instant the user picked, and checks the day it
     * came back as.
     *
     * [askedFor] is the calendar day they tapped. The app does not compute the
     * stored day and must not — it compares its own question against the
     * server's answer, which is the one comparison §3.2 permits and the only one
     * that can catch a zone disagreement. A `null` `due_date` in the response is
     * *not* a mismatch: it is the gateway declining to say, and treating silence
     * as disagreement would raise a false alarm every time.
     */
    fun setDeadline(dueAt: Instant, askedFor: LocalDate) {
        viewModelScope.launch {
            try {
                val updated = todos.setDueAt(todoId, dueAt)
                val stored = updated.dueDate
                _state.update {
                    it.copy(
                        deadlineDayMismatch = if (stored != null && stored != askedFor) {
                            DeadlineDayMismatch(asked = askedFor, stored = stored)
                        } else {
                            null
                        },
                    )
                }
                apply(updated)
            } catch (e: Exception) {
                _state.update { it.copy(transientFailure = e.toFailureReason()) }
            }
        }
    }

    /** Back to the backlog (§5.2), and the tripwire goes quiet with it. */
    fun clearDeadline() {
        _state.update { it.copy(deadlineDayMismatch = null) }
        setDueAt(null)
    }

    fun setTitle(title: String) = mutate { todos.setTitle(todoId, title) }

    fun setDescription(description: String) = mutate { todos.setDescription(todoId, description) }

    /** The task keeps firing. This says "not about this to-do", not "stop". */
    fun dismissFailure() = _state.update { it.copy(transientFailure = null) }

    private fun mutate(block: suspend () -> Todo) {
        viewModelScope.launch {
            try {
                apply(block())
            } catch (e: Exception) {
                // The screen keeps showing what the server last said. No
                // optimistic update means nothing to roll back, and a value
                // that changed and changed back is harder to read than one
                // that never moved.
                _state.update { it.copy(transientFailure = e.toFailureReason()) }
            }
        }
    }

    private fun apply(todo: Todo) {
        _state.update { it.copy(todo = LoadState.Ready(todo)) }
    }
}
