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
import com.ar13x.jarvis.core.voice.NoVoice
import com.ar13x.jarvis.core.voice.VoiceController
import com.ar13x.jarvis.core.voice.VoiceState
import com.ar13x.jarvis.core.voice.toUtterance
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
    /**
     * Defaulted for tests and previews only — **Hilt always injects the real
     * one**, because it generates a call passing every parameter and would fail
     * at compile time if the binding were missing. Nothing in the app ever runs
     * on [NoVoice].
     */
    private val voice: VoiceController = NoVoice,
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationUiState())
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var started = false

    init {
        _state.update { it.copy(micAvailable = voice.available()) }

        // Partials land straight in the composer, so dictation reads as typing
        // that happens to be spoken — and whatever was captured stays there if
        // recognition is stopped halfway.
        viewModelScope.launch {
            voice.state.collect { voiceState ->
                _state.update { current ->
                    when (voiceState) {
                        is VoiceState.Listening -> current.copy(
                            listening = true,
                            amplitude = voiceState.amplitude,
                            voiceError = null,
                            composerText = voiceState.partial.ifEmpty { current.composerText },
                        )
                        is VoiceState.Failed -> current.copy(
                            listening = false,
                            amplitude = 0f,
                            voiceError = voiceState.message,
                        )
                        VoiceState.Idle -> current.copy(listening = false, amplitude = 0f)
                    }
                }
            }
        }

        viewModelScope.launch {
            voice.speakReplies.collect { enabled ->
                _state.update { it.copy(speakReplies = enabled) }
                if (!enabled) voice.stopSpeaking()
            }
        }

        viewModelScope.launch {
            voice.speaking.collect { value -> _state.update { it.copy(speaking = value) } }
        }
    }

    /**
     * Nothing should still be talking after the screen is gone — leaving the
     * engine mid-sentence when someone backs out of a session is the fastest
     * way to make a spoken reply feel like something that escaped.
     */
    override fun onCleared() {
        voice.cancelListening()
        voice.stopSpeaking()
    }

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
            is ConversationEvent.ComposerChanged -> {
                // Typing is a barge-in: someone who has started writing is no
                // longer listening, and talking over them is rude in the same
                // way an unskippable animation is.
                if (_state.value.speaking) voice.stopSpeaking()
                _state.update { it.copy(composerText = event.text) }
            }

            ConversationEvent.ToggleMic -> toggleMic()

            ConversationEvent.ToggleSpeakReplies -> viewModelScope.launch {
                val next = !_state.value.speakReplies
                voice.setSpeakReplies(next)
                if (!next) voice.stopSpeaking()
            }

            ConversationEvent.StopSpeaking -> voice.stopSpeaking()

            ConversationEvent.DismissVoiceError -> {
                voice.clearFailure()
                _state.update { it.copy(voiceError = null) }
            }

            ConversationEvent.Send -> send()
            ConversationEvent.LoadOlder -> loadOlder()
            ConversationEvent.Retry -> _state.value.target?.let { target ->
                viewModelScope.launch { open(target) }
            }

            is ConversationEvent.Confirm -> resolveProposal(event.proposalId, confirm = true)
            is ConversationEvent.Reject -> resolveProposal(event.proposalId, confirm = false)

            is ConversationEvent.ShowMoreOptions -> showMore(event.key, event.cursor)

            is ConversationEvent.CompleteOccurrence ->
                answerOverdue(event.occurrenceId) { agent.completeOccurrence(event.occurrenceId) }

            is ConversationEvent.ExtendOccurrence ->
                answerOverdue(event.occurrenceId) {
                    agent.extendOccurrence(event.occurrenceId, event.minutes)
                }

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
     * An explicit id wins — that is the history sheet. Otherwise the Chat tab
     * opens **empty**: a fresh conversation is the default entry, and going back
     * is what the history button is for. Resuming the last one made that button
     * redundant.
     *
     * "Empty" reuses an existing empty session rather than creating one every
     * time. Otherwise every glance at the Chat tab would leave a session row
     * behind — the same abandoned-session litter already flagged to gw03, except
     * generated far faster. Nothing is lost by reusing one: an empty
     * conversation has no content to distinguish it from another empty one.
     */
    /**
     * Resume a draft, reuse an abandoned blank one, or start fresh — in that
     * order.
     *
     * The middle case is the same anti-litter rule the general session already
     * follows: every press of `+` used to create a session server-side, so a
     * few idle presses left a few empty rows. Reusing an untouched one means the
     * `+` is free until something is actually said.
     */
    private suspend fun openNewTask(sessionId: String?): Session {
        if (sessionId != null) {
            return Session(
                id = sessionId,
                kind = SessionKind.Task,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        }
        val reusable = runCatching { agent.taskSessions() }.getOrNull()
            ?.sessions
            ?.firstOrNull { it.taskId == null && it.isEmpty }
        return if (reusable != null) {
            Session(
                id = reusable.id,
                kind = SessionKind.Task,
                createdAt = reusable.updatedAt,
                updatedAt = reusable.updatedAt,
            )
        } else {
            agent.createSession(SessionKind.Task, taskId = null)
        }
    }

    private suspend fun openGeneral(sessionId: String?): Session {
        if (sessionId != null) {
            return Session(
                id = sessionId,
                kind = SessionKind.General,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        }
        val reusable = runCatching { agent.generalSessions() }.getOrNull()
            ?.sessions
            ?.firstOrNull { it.isEmpty }
        return if (reusable != null) {
            Session(
                id = reusable.id,
                kind = SessionKind.General,
                createdAt = reusable.updatedAt,
                updatedAt = reusable.updatedAt,
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
                is SessionTarget.NewTask -> openNewTask(target.sessionId)
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
    /**
     * Start or stop dictating.
     *
     * Stops any spoken reply first: the phone talking into its own microphone
     * is both comic and a genuine recognition problem, since the engine hears
     * the speaker and transcribes it.
     *
     * Permission is the screen's job — it needs an Activity to ask. This is
     * reached only once permission is granted.
     */
    private fun toggleMic() {
        val current = _state.value
        if (current.isReadOnly) return

        if (current.listening) {
            voice.stopListening()
            return
        }
        voice.stopSpeaking()
        voice.startListening(current.composerText) { finalText ->
            // Fills the composer and stops. It deliberately does **not** send:
            // a transcript is a draft, and auto-sending would put a misheard
            // sentence in front of the model with nobody having read it.
            _state.update { it.copy(composerText = finalText, listening = false, amplitude = 0f) }
        }
    }

    private fun send() {
        val current = _state.value
        val sessionId = current.sessionId ?: return
        val text = current.composerText.trim()
        if (text.isEmpty() || current.thinking || current.isReadOnly) return

        if (current.listening) voice.stopListening()
        voice.stopSpeaking()

        _state.update {
            it.copy(
                composerText = "",
                optimistic = optimisticMessage(text, Instant.now()),
                thinking = true,
            )
        }

        // Captured before the send so the reply can be told from the turn before
        // it. Ids are the gateway's and monotonic, which is a more reliable
        // "newest" than list position.
        val newestBefore = _state.value.newestAssistantId

        viewModelScope.launch {
            runCatching { agent.send(sessionId, text) }
                .onSuccess {
                    // The server now holds both turns, so the optimistic copy is
                    // dropped in the same update that installs the real history —
                    // clearing it first would blink the message out and back.
                    refreshHistory(sessionId, clearOptimistic = true)

                    // Spoken from the refreshed history rather than from the
                    // response body, so that what is heard is what is on screen.
                    // The screen renders persisted history; speaking the response
                    // instead meant the two disagreed whenever the server's copy
                    // differed from what it returned — and once, when a turn was
                    // not persisted at all, it would have read out an apology
                    // over a blank screen.
                    //
                    // Silence when nothing new arrived is the point, not a gap:
                    // a failed refresh or an unpersisted turn both leave the
                    // screen unchanged, and voice should say as much as the
                    // screen does.
                    val state = _state.value
                    val reply = state.newestAssistantMessage()
                    if (state.speakReplies && reply != null && reply.id != newestBefore) {
                        voice.speak(reply.toUtterance())
                    }
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
    /**
     * Answering the agent's overdue question.
     *
     * Refreshes history afterwards rather than patching the card locally: the
     * server rewrites the component with its resolution and the new counts, and
     * guessing at them here is how the displayed allowance drifts from the one
     * actually being enforced.
     */
    private fun answerOverdue(occurrenceId: Long, action: suspend () -> com.ar13x.jarvis.core.model.Task) {
        val sessionId = _state.value.sessionId ?: return
        if (_state.value.resolvingOverdue != null) return

        viewModelScope.launch {
            _state.update { it.copy(resolvingOverdue = occurrenceId) }
            runCatching { action() }
                .onSuccess { task ->
                    _state.update { it.copy(resolvingOverdue = null, task = task) }
                    refreshHistory(sessionId, clearOptimistic = false)
                }
                .onFailure { error ->
                    // Includes running out of extensions, which the gateway
                    // rejects rather than the app pre-empting — see the
                    // repository doc. §3.5: nothing is queued and retried.
                    _state.update {
                        it.copy(
                            resolvingOverdue = null,
                            transientFailure = error.toFailureReason(),
                        )
                    }
                }
        }
    }

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
