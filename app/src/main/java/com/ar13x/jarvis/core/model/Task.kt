package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/** Plan §4.3. Field names and nullability mirror the gateway contract exactly. */
@Serializable
data class Task(
    val id: Long,
    val title: String,
    val description: String = "",

    @Serializable(InstantSerializer::class)
    @SerialName("due_at") val dueAt: Instant,

    /**
     * The **local** calendar day, sent by the server. This is the display key.
     *
     * Never derive it from [dueAt]: the server runs UTC, so a 9 PM Sydney
     * reminder falls on the next UTC day. That is the parent plan's
     * highest-risk defect and it is invisible until it bites.
     */
    @Serializable(LocalDateSerializer::class)
    @SerialName("due_date") val dueDate: LocalDate,

    @SerialName("is_priority") val isPriority: Boolean = false,

    /**
     * RFC 5545 RRULE, or null for a one-shot task.
     *
     * **The app never parses or generates this.** It has no recurrence library
     * and needs none — display [recurrenceText] instead (plan §3.1).
     */
    val recurrence: String? = null,

    /** Server-rendered by `describe_recurrence()`. This is what you display. */
    @SerialName("recurrence_text") val recurrenceText: String? = null,

    @Serializable(InstantSerializer::class)
    @SerialName("next_fire_at") val nextFireAt: Instant? = null,

    val status: TaskStatus = TaskStatus.Active,

    /** Server-computed against the user's local day. Never computed here (§3.2). */
    @SerialName("due_today") val dueToday: Boolean = false,

    @Serializable(InstantSerializer::class)
    @SerialName("created_at") val createdAt: Instant,
    @Serializable(InstantSerializer::class)
    @SerialName("updated_at") val updatedAt: Instant,
    @Serializable(InstantSerializer::class)
    @SerialName("completed_at") val completedAt: Instant? = null,
    @Serializable(InstantSerializer::class)
    @SerialName("cancelled_at") val cancelledAt: Instant? = null,
) {
    val isRecurring: Boolean get() = recurrence != null
}
