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

    /**
     * The follow-up loop's state, carried here so the *notification* can answer
     * for it (docs/joy-to-gw03-07 §3.1).
     *
     * All defaulted: a gateway that predates the loop still parses, and the
     * defaults describe the behaviour of a task with a full allowance, which is
     * what an occurrence from such a gateway effectively has.
     */
    @SerialName("extensions_used") val extensionsUsed: Int = 0,
    @SerialName("extensions_allowed") val extensionsAllowed: Int = 2,
    /**
     * How long an unanswered nudge waits before the server auto-extends it.
     *
     * **Load-bearing, not informational.** The app schedules its catch-up poll
     * from this (09 §7). Assuming 15 would silently desynchronise the moment
     * gw03 tunes it, and the symptom would be alarms armed against deadlines
     * that had already moved.
     */
    @SerialName("grace_minutes") val graceMinutes: Int = 15,
    /** What the chips offer. Server-owned policy, same argument as the cap. */
    @SerialName("extension_minutes") val extensionMinutes: List<Int> = listOf(15, 30, 60),
) {
    val extensionsLeft: Int get() = (extensionsAllowed - extensionsUsed).coerceAtLeast(0)
    val canExtend: Boolean get() = extensionsLeft > 0
}
