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
    /**
     * The reminders chasing this to-do, resolved from [Todo.taskIds].
     *
     * Fetched separately because the to-do carries ids, not tasks — §5.1's link
     * direction. A task that cannot be fetched is simply absent here rather than
     * failing the whole screen: an unlinkable id should not take the to-do down
     * with it.
     */
    val reminders: List<Task> = emptyList(),
    val remindersLoading: Boolean = false,
    val transientFailure: FailureReason? = null,
    /**
     * Reminders that could be pointed at this to-do, loaded only when asked.
     *
     * Live tasks minus the ones already linked here. A task belonging to a
     * *different* to-do is still offered, deliberately: the app cannot know
     * which without fetching every to-do, and the gateway answers `409` naming
     * the owner. Showing an honest error beats hiding a row for a reason the
     * user cannot see, and it beats fetching the world to pre-empt it.
     */
    val candidates: LoadState<List<Task>>? = null,
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
 * One to-do, and the reminders pointed at it.
 *
 * Every edit here is direct, with no proposal, and that is §5.4's rule rather
 * than an exception to it: the model confirms because it can misparse "next
 * Thursday"; a tap on a status or a deadline cannot.
 */
@HiltViewModel
class TodoDetailViewModel @Inject constructor(
    private val todos: TodoRepository,
    private val tasks: TaskRepository,
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
    fun unlink(taskId: Long) = mutate { todos.unlink(todoId, taskId) }

    /**
     * Opens the picker and fetches what could be linked.
     *
     * `active` and `awaiting` only — the states in which a reminder still
     * fires. Pointing a completed or cancelled task at a to-do would attach
     * something that will never chase it, which looks like setting a reminder
     * and is not one.
     */
    fun openPicker() {
        _state.update { it.copy(candidates = LoadState.Loading) }
        viewModelScope.launch {
            val linked = _state.value.todo.dataOrNull?.taskIds.orEmpty().toSet()
            _state.update { current ->
                current.copy(
                    candidates = try {
                        LoadState.Ready(
                            tasks.tasks(statuses = setOf(TaskStatus.Active, TaskStatus.Awaiting))
                                .tasks
                                .filterNot { it.id in linked },
                        )
                    } catch (e: Exception) {
                        LoadState.Failed(e.toFailureReason())
                    },
                )
            }
        }
    }

    fun closePicker() = _state.update { it.copy(candidates = null) }

    /**
     * Points an existing reminder at this to-do.
     *
     * A task, not a copy (§5.1). A `409` means it already belongs to another
     * to-do, and the gateway's message names which — shown verbatim, because
     * "that reminder is already on X" is the only useful thing to say and the
     * app does not know X.
     */
    fun link(taskId: Long) {
        closePicker()
        mutate { todos.link(todoId, taskId) }
    }

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

    private suspend fun apply(todo: Todo) {
        _state.update { it.copy(todo = LoadState.Ready(todo), remindersLoading = todo.hasReminders) }
        loadReminders(todo)
    }

    private suspend fun loadReminders(todo: Todo) {
        if (todo.taskIds.isEmpty()) {
            _state.update { it.copy(reminders = emptyList(), remindersLoading = false) }
            return
        }
        val fetched = viewModelScope.async {
            todo.taskIds
                .map { id -> viewModelScope.async { runCatching { tasks.task(id) }.getOrNull() } }
                .awaitAll()
                .filterNotNull()
        }.await()

        _state.update { it.copy(reminders = fetched, remindersLoading = false) }
    }
}
