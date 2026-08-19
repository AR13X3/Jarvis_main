package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    @SerialName("active") Active,
    @SerialName("awaiting") Awaiting,
    @SerialName("completed") Completed,
    @SerialName("cancelled") Cancelled,
    @SerialName("incomplete") Incomplete;

    /**
     * **Only `completed` and `cancelled` lock a task** (plan §4.3).
     *
     * `incomplete` is *not* terminal — it keeps its full mutation set and is the
     * one you most want to reschedule. Treating it as terminal makes lapsed
     * tasks unreschedulable, which is the opposite of what they are for.
     */
    val isTerminal: Boolean get() = this == Completed || this == Cancelled

    /** Terminal sessions show an explanatory strip instead of a composer (§5.3). */
    val allowsMutation: Boolean get() = !isTerminal
}
