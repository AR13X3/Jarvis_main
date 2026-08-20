package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * One past conversation, for the Chat tab's history (BUILD_NOTES §3.12).
 *
 * A task session is **one** thread bound to one task forever. General chat is a
 * series of conversations — you ask about something, get pointed at a task, and
 * that exchange is worth keeping. So the gateway creating a new general session
 * per `POST` is the right primitive; this is the list that makes them reachable.
 */
@Serializable
data class SessionSummary(
    val id: String,
    val kind: SessionKind,
    @SerialName("task_id") val taskId: Long? = null,
    /**
     * Server-derived from the first user turn. Nullable because a conversation
     * can be opened and abandoned before anything is said.
     */
    val title: String? = null,
    @Serializable(InstantSerializer::class)
    @SerialName("updated_at") val updatedAt: Instant,
    @SerialName("message_count") val messageCount: Int = 0,
) {
    /** Nothing was ever said in it, so there is nothing to go back to. */
    val isEmpty: Boolean get() = messageCount == 0
}

@Serializable
data class PagedSessions(
    val sessions: List<SessionSummary> = emptyList(),
    val page: Int = 1,
    @SerialName("has_more") val hasMore: Boolean = false,
)
