package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
enum class SessionKind {
    @SerialName("task") Task,
    @SerialName("general") General,
}

/**
 * One session is bound to at most one task (parent plan §2.2) — enforced by a
 * unique constraint on `agent.sessions.task_id`, not by app logic.
 */
@Serializable
data class Session(
    val id: String,
    val kind: SessionKind,
    @SerialName("task_id") val taskId: Long? = null,
    @Serializable(InstantSerializer::class)
    @SerialName("created_at") val createdAt: Instant,
    @Serializable(InstantSerializer::class)
    @SerialName("updated_at") val updatedAt: Instant,
)
