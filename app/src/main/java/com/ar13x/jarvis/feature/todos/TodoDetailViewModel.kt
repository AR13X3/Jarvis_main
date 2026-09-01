package com.ar13x.jarvis.feature.todos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.core.data.TaskRepository
import com.ar13x.jarvis.core.data.TodoRepository
import com.ar13x.jarvis.core.data.toFailureReason
import com.ar13x.jarvis.core.model.FailureReason
import com.ar13x.jarvis.core.model.Task
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
)

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
    fun setDueAt(dueAt: java.time.Instant?) = mutate { todos.setDueAt(todoId, dueAt) }

    fun setTitle(title: String) = mutate { todos.setTitle(todoId, title) }

    fun setDescription(description: String) = mutate { todos.setDescription(todoId, description) }

    /** The task keeps firing. This says "not about this to-do", not "stop". */
    fun unlink(taskId: Long) = mutate { todos.unlink(todoId, taskId) }

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
