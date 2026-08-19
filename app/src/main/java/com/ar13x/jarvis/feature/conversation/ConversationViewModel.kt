package com.ar13x.jarvis.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.core.data.AgentRepository
import com.ar13x.jarvis.core.data.TaskRepository
import com.ar13x.jarvis.core.data.toFailureReason
import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.Session
import com.ar13x.jarvis.core.model.SessionKind
import com.ar13x.jarvis.core.ui.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private const val HISTORY_PAGE = 30

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val agent: AgentRepository,
    private val tasks: TaskRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationUiState())
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var started = false

    /**
     * Idempotent: the screen calls this from a `LaunchedEffect`, which re-runs on
     * configuration change, and re-resolving the session there would create a
     * second one for the same task.
     */
    fun start(target: SessionTarget) {
        if (started && _state.value.target == target) return
        started = true
        _state.update { it.copy(target = target) }
        viewModelScope.launch { open(target) }
    }

    fun onEvent(event: ConversationEvent) {
        when (event) {
            is ConversationEvent.ComposerChanged ->
                _state.update { it.copy(composerText = event.text) }

            ConversationEvent.Send -> send()
            ConversationEvent.LoadOlder -> loadOlder()
            ConversationEvent.Retry -> _state.value.target?.let { target ->
                viewModelScope.launch { open(target) }
            }

            is ConversationEvent.Confirm -> resolveProposal(event.proposalId, confirm = true)
            is ConversationEvent.Reject -> resolveProposal(event.proposalId, confirm = false)

            is ConversationEvent.ShowMoreOptions -> showMore(event.key, event.cursor)

            ConversationEvent.DismissFailure ->
                _state.update { it.copy(transientFailure = null) }
        }
    }

    private suspend fun open(target: SessionTarget) {
        _state.update { it.copy(history = LoadState.Loading) }
        runCatching {
            val session: Session = when (target) {
                is SessionTarget.Bound -> agent.sessionForTask(target.taskId)
                SessionTarget.NewTask -> agent.createSession(SessionKind.Task, taskId = null)
                SessionTarget.General -> agent.generalSession()
            }
            // A bound session's task is fetched separately: the session says
            // which task it is, the task says whether it is still mutable, and
            // the composer depends on the second.
            val task = session.taskId?.let { tasks.task(it) }
            val page = agent.messages(session.id, before = null, limit = HISTORY_PAGE)
            Triple(session, task, page)
        }.onSuccess { (session, task, page) ->
            _state.update {
                it.copy(
                    sessionId = session.id,
                    task = task,
                    history = LoadState.Ready(page.messages),
                    hasMoreHistory = page.hasMore,
                )
            }
        }.onFailure { error ->
            _state.update { it.copy(history = LoadState.Failed(error.toFailureReason())) }
        }
    }

    /**
     * Optimistic send (plan §5.3): the user's message appears immediately and a
     * pending agent bubble shows a thinking indicator until the response lands.
     *
     * At a ~4s median that state is on screen constantly, so it is a first-class
     * part of the design rather than a spinner bolted on afterwards.
     */
    private fun send() {
        val current = _state.value
        val sessionId = current.sessionId ?: return
        val text = current.composerText.trim()
        if (text.isEmpty() || current.thinking || current.isReadOnly) return

        _state.update {
            it.copy(
                composerText = "",
                optimistic = optimisticMessage(text, Instant.now()),
                thinking = true,
            )
        }

        viewModelScope.launch {
            runCatching { agent.send(sessionId, text) }
                .onSuccess {
                    // The server now holds both turns, so the optimistic copy is
                    // dropped in the same update that installs the real history —
                    // clearing it first would blink the message out and back.
                    refreshHistory(sessionId, clearOptimistic = true)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            optimistic = null,
                            thinking = false,
                            // Nothing was queued, so the text goes back in the
                            // composer where the user can see it and retry.
                            composerText = text,
                            transientFailure = error.toFailureReason(),
                        )
                    }
                }
        }
    }

    private suspend fun refreshHistory(sessionId: String, clearOptimistic: Boolean) {
        runCatching { agent.messages(sessionId, before = null, limit = HISTORY_PAGE) }
            .onSuccess { page ->
                _state.update {
                    it.copy(
                        history = LoadState.Ready(page.messages),
                        hasMoreHistory = page.hasMore,
                        optimistic = if (clearOptimistic) null else it.optimistic,
                        thinking = false,
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(thinking = false, transientFailure = error.toFailureReason())
                }
            }
    }

    /** Pages backwards through history via `before` (plan §5.3). */
    private fun loadOlder() {
        val current = _state.value
        val sessionId = current.sessionId ?: return
        val oldest = current.history.dataOrNull?.firstOrNull()?.id ?: return
        if (!current.hasMoreHistory || current.loadingOlder) return

        viewModelScope.launch {
            _state.update { it.copy(loadingOlder = true) }
            runCatching { agent.messages(sessionId, before = oldest, limit = HISTORY_PAGE) }
                .onSuccess { page ->
                    _state.update { state ->
                        val existing = state.history.dataOrNull.orEmpty()
                        state.copy(
                            loadingOlder = false,
                            history = LoadState.Ready(page.messages + existing),
                            hasMoreHistory = page.hasMore,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loadingOlder = false, transientFailure = error.toFailureReason())
                    }
                }
        }
    }

    /**
     * Confirming is the only thing in the app that writes a task through the
     * agent (parent plan §2.4). Rejecting writes nothing at all — which is
     * exactly what acceptance item 4 checks.
     */
    private fun resolveProposal(proposalId: String, confirm: Boolean) {
        val sessionId = _state.value.sessionId ?: return
        if (_state.value.resolving != null) return

        viewModelScope.launch {
            _state.update { it.copy(resolving = proposalId) }
            runCatching {
                if (confirm) agent.confirmProposal(proposalId) else { agent.rejectProposal(proposalId); null }
            }.onSuccess { task ->
                _state.update {
                    it.copy(
                        resolving = null,
                        // A confirmed create binds this session to the new task,
                        // and a confirmed complete or cancel may have made the
                        // task terminal — either way the composer's fate changed.
                        task = task ?: it.task,
                    )
                }
                refreshHistory(sessionId, clearOptimistic = false)
            }.onFailure { error ->
                _state.update {
                    it.copy(resolving = null, transientFailure = error.toFailureReason())
                }
            }
        }
    }

    /** "Show more" appends the next three options to that card (plan §5.4). */
    private fun showMore(key: OptionsKey, cursor: String) {
        val sessionId = _state.value.sessionId ?: return
        if (_state.value.options[key]?.loading == true) return

        viewModelScope.launch {
            _state.update { state ->
                val existing = state.options[key] ?: OptionsState()
                state.copy(options = state.options + (key to existing.copy(loading = true)))
            }
            runCatching { agent.moreTaskOptions(sessionId, cursor) }
                .onSuccess { response ->
                    val more = response.components
                        .filterIsInstance<AgentComponent.TaskOptions>()
                        .firstOrNull()
                    _state.update { state ->
                        val existing = state.options[key] ?: OptionsState()
                        state.copy(
                            options = state.options + (
                                key to existing.copy(
                                    extra = existing.extra + more?.options.orEmpty(),
                                    cursor = more?.moreCursor,
                                    loading = false,
                                )
                                ),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { state ->
                        val existing = state.options[key] ?: OptionsState()
                        state.copy(
                            options = state.options + (key to existing.copy(loading = false)),
                            transientFailure = error.toFailureReason(),
                        )
                    }
                }
        }
    }
}
