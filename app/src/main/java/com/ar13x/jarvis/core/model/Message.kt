package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
enum class MessageRole {
    @SerialName("user") User,
    @SerialName("assistant") Assistant,
    @SerialName("tool") Tool,
}

/**
 * A persisted turn.
 *
 * Assistant messages carry the same [components] the live [AgentResponse] did,
 * which is what lets a resolved confirmation card stay visible in history in its
 * resolved state — scrolling back should show what you agreed to, not a blank
 * (plan §5.3).
 */
@Serializable
data class Message(
    val id: Long,
    val role: MessageRole,
    val text: String = "",
    val components: List<AgentComponent> = emptyList(),
    @Serializable(InstantSerializer::class)
    @SerialName("created_at") val createdAt: Instant,
)

@Serializable
data class PagedMessages(
    val messages: List<Message> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)
