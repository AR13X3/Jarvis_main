package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * `GET /occurrences/upcoming` (plan §4.4).
 *
 * Flagged in §13 as an **addition** the gateway must implement: the parent plan
 * does not list it, but §2.8 requires the device to mirror a ~48h window of
 * occurrences to set exact alarms, and no other endpoint serves them.
 */
@Serializable
data class UpcomingOccurrences(
    val occurrences: List<UpcomingOccurrence> = emptyList(),
    @SerialName("window_hours") val windowHours: Int = 48,
    @Serializable(InstantSerializer::class)
    @SerialName("generated_at") val generatedAt: Instant,
)

@Serializable
data class UpcomingOccurrence(
    @SerialName("occurrence_id") val occurrenceId: Long,
    @SerialName("task_id") val taskId: Long,
    val title: String,
    @Serializable(InstantSerializer::class)
    @SerialName("scheduled_for") val scheduledFor: Instant,
    @SerialName("is_priority") val isPriority: Boolean = false,
)
