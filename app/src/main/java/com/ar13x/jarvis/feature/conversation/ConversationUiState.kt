package com.ar13x.jarvis.feature.conversation

import androidx.compose.runtime.Immutable
import com.ar13x.jarvis.core.model.FailureReason
import com.ar13x.jarvis.core.model.Message
import com.ar13x.jarvis.core.model.MessageRole
import com.ar13x.jarvis.core.model.SessionSummary
import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.model.TaskOption
import com.ar13x.jarvis.core.model.TaskStatus
import com.ar13x.jarvis.core.ui.LoadState

/**
 * Which conversation this is.
 *
 * Resolved by the screen and handed to the ViewModel, rather than dug out of
 * navigation arguments. The Chat tab's route carries no id at all, so a
 * SavedStateHandle lookup would have to guess from an absent key — this way
 * every caller states plainly what it wants.
 */
sealed interface SessionTarget {
    /** That task's persistent session, with its full history (plan §5.3). */
    data class Bound(val taskId: Long) : SessionTarget

    /**
     * The `+`, or a handoff from general chat — a new unbound session, which is
     * the only kind that can propose a create (§5.2, parent plan §2.3).
     *
     * [seed] is sent automatically once the session exists.
     */
    data class NewTask(
        val seed: String? = null,
        /**
         * Resume this session instead of starting one.
         *
         * Set when re-entering an unfinished draft from the task list. Without
         * it every entry created a fresh session, which is how a conversation
         * could be abandoned and never found again.
         */
        val sessionId: String? = null,
    ) : SessionTarget

    /**
     * The Chat tab (§5.4).
     *
     * [sessionId] null means "the most recent conversation, or a new one if
     * there are none" — the Chat tab resumes rather than starting blank.
     */
    data class General(val sessionId: String? = null) : SessionTarget
}

/**
 * Identifies one `task_options` component inside the stream.
 *
 * "Show more" appends to a *particular* card, and a session can hold several
 * disambiguation cards from different turns, so the message alone is not enough
 * to say which one grew.
 */
@Immutable
data class OptionsKey(val messageId: Long, val index: Int)

@Immutable
data class OptionsState(
    val extra: List<TaskOption> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = false,
)

@Immutable
data class ConversationUiState(
    val target: SessionTarget? = null,
    val sessionId: String? = null,
    /** Null for the general session and for an unbound one, until it binds. */
    val task: Task? = null,
    val history: LoadState<List<Message>> = LoadState.Loading,
    val hasMoreHistory: Boolean = false,
    val loadingOlder: Boolean = false,

    /**
     * The user's message, shown the instant they send it.
     *
     * Optimistic display only — it is replaced by the server's copy on the next
     * fetch. This is not the offline write queue §3.5 forbids: nothing is
     * stored, nothing is retried, and a failure removes it again.
     */
    val optimistic: Message? = null,
    /** The agent is composing. Rendered as a bubble, never as a spinner (§5.3). */
    val thinking: Boolean = false,

    val composerText: String = "",

    /** False when the device has no recogniser at all — the mic is then hidden. */
    val micAvailable: Boolean = false,
    val listening: Boolean = false,
    /** Input level 0..1 while listening, for the mic ring. */
    val amplitude: Float = 0f,
    /** Off by default — see `VoiceSettings`. */
    val speakReplies: Boolean = false,
    val speaking: Boolean = false,
    /** Recognition failed. Shown in the composer area, never as a dialog. */
    val voiceError: String? = null,

    /** Proposal id currently being confirmed or rejected. */
    val resolving: String? = null,
    /** Occurrence id currently being completed or extended, for the overdue card. */
    val resolvingOverdue: Long? = null,
    val options: Map<OptionsKey, OptionsState> = emptyMap(),
    /**
     * Status looked up per task, for options the gateway sent without one.
     *
     * Interim: `TaskOption.status` is requested as BUILD_NOTES §3.13, and the
     * moment it arrives this map stops being filled, because nothing is fetched
     * for an option that already knows its own status. Self-retiring rather than
     * dead code waiting to be deleted.
     */
    val optionStatus: Map<Long, TaskStatus> = emptyMap(),
    val transientFailure: FailureReason? = null,
    /** Past conversations, loaded when the history sheet is opened. */
    val conversations: LoadState<List<SessionSummary>>? = null,
) {
    /**
     * Terminal tasks are read-only (plan §5.3). The composer is *replaced* by an
     * explanation rather than disabled with no reason given — questions still
     * work, the server simply offers no mutation tools (parent plan §2.3).
     */
    val isReadOnly: Boolean get() = task?.status?.isTerminal == true

    val canSend: Boolean
        get() = composerText.isNotBlank() && !thinking && sessionId != null && !isReadOnly

    /**
     * Newest first, because the list is `reverseLayout` (§5.3). The optimistic
     * message is newer than anything the server has returned, so it leads.
     */
    val stream: List<Message>
        get() = buildList {
            optimistic?.let { add(it) }
            addAll(history.dataOrNull.orEmpty().asReversed())
        }

    /** Nothing said yet — the screen shows its oversized headline instead. */
    val isEmpty: Boolean
        get() = history is LoadState.Ready && stream.isEmpty() && !thinking
}

/** Optimistic messages get negative ids so they cannot collide with real ones. */
internal const val OPTIMISTIC_MESSAGE_ID = -1L

internal fun optimisticMessage(text: String, at: java.time.Instant) = Message(
    id = OPTIMISTIC_MESSAGE_ID,
    role = MessageRole.User,
    text = text,
    createdAt = at,
)

sealed interface ConversationEvent {
    data class ComposerChanged(val text: String) : ConversationEvent
    data object Send : ConversationEvent
    data object LoadOlder : ConversationEvent
    data object Retry : ConversationEvent

    data class Confirm(val proposalId: String) : ConversationEvent
    data class Reject(val proposalId: String) : ConversationEvent

    data class ShowMoreOptions(val key: OptionsKey, val cursor: String) : ConversationEvent

    data object DismissFailure : ConversationEvent

    /** Chat history (§3.12). */
    data object LoadConversations : ConversationEvent
    data class OpenConversation(val sessionId: String) : ConversationEvent
    data object NewConversation : ConversationEvent

    /** Answering an overdue nudge (docs/joy-to-gw03-07). */
    data class CompleteOccurrence(val occurrenceId: Long) : ConversationEvent
    data class ExtendOccurrence(val occurrenceId: Long, val minutes: Int) : ConversationEvent

    /** Voice. Dictation fills the composer; it never sends and never confirms. */
    data object ToggleMic : ConversationEvent
    data object ToggleSpeakReplies : ConversationEvent
    data object StopSpeaking : ConversationEvent
    data object DismissVoiceError : ConversationEvent
}
