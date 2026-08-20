package com.ar13x.jarvis.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.core.data.AgentRepository
import com.ar13x.jarvis.core.data.TaskRepository
import com.ar13x.jarvis.core.data.toFailureReason
import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.Session
import com.ar13x.jarvis.core.model.SessionSummary
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

            ConversationEvent.LoadConversations -> loadConversations()

            is ConversationEvent.OpenConversation -> {
                started = false
                start(SessionTarget.General(event.sessionId))
            }

            ConversationEvent.NewConversation -> viewModelScope.launch {
                runCatching { agent.newGeneralSession() }
                    .onSuccess { session ->
                        started = false
                        start(SessionTarget.General(session.id))
                    }
                    .onFailure { error ->
                        _state.update { it.copy(transientFailure = error.toFailureReason()) }
                    }
            }
        }
    }

    /**
     * Resolves which general conversation to show.
     *
     * Explicit id wins; otherwise the most recent one that actually has
     * messages, and only a genuinely new conversation if there are none. That
     * last condition matters — every `+` and every abandoned launch leaves an
     * empty session behind, and resuming into one of those would look exactly
     * like the history being lost.
     */
    private suspend fun openGeneral(sessionId: String?): Session {
        if (sessionId != null) {
            return Session(
                id = sessionId,
                kind = SessionKind.General,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        }
        val recent = runCatching { agent.generalSessions() }.getOrNull()
            ?.sessions
            ?.firstOrNull { !it.isEmpty }
        return if (recent != null) {
            Session(
                id = recent.id,
                kind = SessionKind.General,
                createdAt = recent.updatedAt,
                updatedAt = recent.updatedAt,
            )
        } else {
            agent.newGeneralSession()
        }
    }

    private suspend fun open(target: SessionTarget) {
        _state.update { it.copy(history = LoadState.Loading) }
        runCatching {
            val session: Session = when (target) {
                is SessionTarget.Bound -> agent.sessionForTask(target.taskId)
                is SessionTarget.NewTask -> agent.createSession(SessionKind.Task, taskId = null)
                is SessionTarget.General -> openGeneral(target.sessionId)
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
            // A seeded session sends straight away rather than waiting for the
            // user to press send on text they have already written once.
            val seed = (target as? SessionTarget.NewTask)?.seed
            if (!seed.isNullOrBlank()) {
                _state.update { it.copy(composerText = seed) }
                send()
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

    /**
     * Fills in the status of any option the gateway sent without one.
     *
     * "What's due today" lists everything due today, and a task already done
     * must not look identical to one still outstanding — but `TaskOption` does
     * not carry a status yet (BUILD_NOTES §3.13). Until it does, the app asks.
     *
     * Bounded and self-retiring: at most three options are on screen per page,
     * each already-seen id is skipped, and an option that arrives *with* a
     * status is never fetched at all — so this stops doing anything the day the
     * field lands, without a line changing.
     */
    private fun enrichOptionStatuses() {
        val state = _state.value
        val missing = state.stream
            .flatMap { it.components }
            .filterIsInstance<AgentComponent.TaskOptions>()
            .flatMap { it.options }
            .filter { it.status == null }
            .map { it.taskId }
            .distinct()
            .filterNot { it in state.optionStatus }
        if (missing.isEmpty()) return

        viewModelScope.launch {
            val found = missing.mapNotNull { id ->
                // A failure here is cosmetic: the button simply shows no status,
                // which is exactly where it was before. Not worth surfacing.
                runCatching { tasks.task(id) }.getOrNull()?.let { id to it.status }
            }
            if (found.isEmpty()) return@launch
            _state.update { it.copy(optionStatus = it.optionStatus + found) }
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
                enrichOptionStatuses()
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
                .onSuccess { more ->
                    _state.update { state ->
                        val existing = state.options[key] ?: OptionsState()
                        state.copy(
                            options = state.options + (
                                key to existing.copy(
                                    extra = existing.extra + more.options,
                                    cursor = more.moreCursor,
                                    loading = false,
                                )
                                ),
                        )
                    }
                    enrichOptionStatuses()
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

    private fun loadConversations() {
        viewModelScope.launch {
            _state.update { it.copy(conversations = LoadState.Loading) }
            runCatching { agent.generalSessions() }
                .onSuccess { page ->
                    _state.update {
                        // Empty conversations are hidden: they are the residue of
                        // opening the tab and saying nothing, and a history list
                        // full of blanks is worse than a short one.
                        it.copy(conversations = LoadState.Ready(page.sessions.filterNot(SessionSummary::isEmpty)))
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(conversations = LoadState.Failed(error.toFailureReason())) }
                }
        }
    }
}
