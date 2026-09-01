package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * `GET /dashboard` — v2 plan §6.
 *
 * Field names and nullability mirror the served contract exactly
 * (sha256 `e398ff18e4aa6b33`, 24 paths, verified against the Taildropped copy
 * on 2026-09-01). Where the contract says *required*, the field here is
 * non-null **and carries no default**, including the lists.
 *
 * That last part is deliberate and is the whole argument of this screen. A
 * `= emptyList()` default would turn "the gateway did not send this section"
 * into "this section is empty", and those are the two sentences §6 exists to
 * keep apart. An absent required list should fail loudly at parse time, where
 * it is one exception in a log, rather than quietly at render time, where it is
 * a clean week nobody recorded.
 *
 * The gateway computes every number here. Nothing in this file derives one.
 */
@Serializable
data class Dashboard(
    val period: DashboardPeriod,
    val totals: DashboardTotals = DashboardTotals(),

    /** §6 `failed`, grouped by task so repeat offenders are visible. */
    val failing: List<FailingTask>,

    /** §6 `drifting` over tasks. Read [provenance] before rendering it empty. */
    val drifting: List<DriftingTask>,
    val provenance: DashboardProvenance = DashboardProvenance(),

    /** §6 `missed` over routine slots. Read [routineProvenance] before rendering it empty. */
    val missed: List<MissedSlot>,
    @SerialName("drifting_slots") val driftingSlots: List<DriftingSlot>,
    @SerialName("routine_provenance") val routineProvenance: RoutineProvenance,

    /** §6 `stale` over undated to-dos. Not windowed — see [DashboardPeriod.isHistorical]. */
    val stale: List<StaleTodo>,

    val policy: DashboardPolicy,
)

/**
 * The window, and which day it was bucketed on.
 *
 * `bucketed_by` is stated rather than assumed: §3 settled that "what did I
 * miss" is the day something was **due**, and the app must not re-bucket.
 */
@Serializable
data class DashboardPeriod(
    @Serializable(LocalDateSerializer::class)
    @SerialName("date_from") val dateFrom: LocalDate,
    @Serializable(LocalDateSerializer::class)
    @SerialName("date_to") val dateTo: LocalDate,
    @SerialName("bucketed_by") val bucketedBy: String = "due",
) {
    /**
     * True when the window closed before [today].
     *
     * Drives one thing only: whether the `stale` section has to say it is not
     * windowed. `stale` is a fact about *now* and deliberately ignores this
     * period (§5.2), so in a historical window it is the one section that keeps
     * changing — which reads as a bug unless it is labelled. When the window
     * ends today the label says nothing and is omitted.
     */
    fun isHistorical(today: LocalDate): Boolean = dateTo.isBefore(today)
}

@Serializable
data class DashboardTotals(
    val firings: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val cancelled: Int = 0,
    val unresolved: Int = 0,
)

/**
 * Whether the **task** numbers are measuring anything yet.
 *
 * Not a footnote. `firings_without_history` counts firings the event stream
 * cannot speak for because they predate it; on the day the route shipped that
 * was 27, and `drifting` was consequently empty. An empty drift list rendered
 * as "nothing is drifting" would be a lie for about a week, and it errs in the
 * direction that reassures.
 */
@Serializable
data class DashboardProvenance(
    @Serializable(InstantSerializer::class)
    @SerialName("observed_from") val observedFrom: Instant? = null,
    @SerialName("events_observed") val eventsObserved: Int = 0,
    @SerialName("events_reconstructed") val eventsReconstructed: Int = 0,
    @SerialName("firings_without_history") val firingsWithoutHistory: Int = 0,
) {
    /** True when `drifting` cannot be read as a verdict, empty or not. */
    val incomplete: Boolean get() = firingsWithoutHistory > 0
}

/**
 * Whether the **routine** numbers are measuring anything yet.
 *
 * The same trap as [DashboardProvenance], in a worse place. Before anyone taps
 * a slot, every tracked slot of every finished day has no start — so a naive
 * `missed` reports the whole week as missed, which is indistinguishable from
 * "the routine tab has not been used yet". The gateway counts `missed` only
 * within days that carry at least one start; [daysMeasured] is how many days
 * that was, and [daysElapsed] is how many there were.
 */
@Serializable
data class RoutineProvenance(
    @SerialName("days_elapsed") val daysElapsed: Int,
    @SerialName("days_measured") val daysMeasured: Int,
    @Serializable(LocalDateSerializer::class)
    @SerialName("logging_began_on") val loggingBeganOn: LocalDate? = null,
) {
    /**
     * Nothing was measured, so nothing can be concluded.
     *
     * An empty `missed` under this is **not** a clean week, and rendering it as
     * one congratulates Joy for a week nobody recorded.
     */
    val measuredNothing: Boolean get() = daysMeasured <= 0

    /** Some days are unaccounted for. Shown always, not only when the gap is large. */
    val hasGap: Boolean get() = daysElapsed > daysMeasured
}

/**
 * The thresholds, on the wire, because a number the client cannot see is a
 * number nobody can argue with.
 *
 * Every field is required by the contract and none has a default here. That is
 * the point: [driftThresholdMinutes] is derived as
 * `grace + extensionsAllowed * autoExtendMinutes`, and all three inputs ride
 * along so the derivation is *checkable* rather than a magic number the client
 * repeats back.
 */
@Serializable
data class DashboardPolicy(
    @SerialName("drift_threshold_minutes") val driftThresholdMinutes: Int,
    @SerialName("drift_min_firings") val driftMinFirings: Int,
    @SerialName("grace_minutes") val graceMinutes: Int,
    @SerialName("extensions_allowed") val extensionsAllowed: Int,
    @SerialName("auto_extend_minutes") val autoExtendMinutes: Int,
    @SerialName("routine_drift_threshold_minutes") val routineDriftThresholdMinutes: Int,
    @SerialName("routine_drift_min_starts") val routineDriftMinStarts: Int,
) {
    /**
     * Whether the task threshold is the derivation it claims to be.
     *
     * Not used to *correct* anything — the gateway owns the number. It exists
     * so a disagreement shows up in a test rather than as a screen and a
     * database quietly meaning different things by "drifting".
     */
    val taskThresholdIsDerived: Boolean
        get() = driftThresholdMinutes == graceMinutes + extensionsAllowed * autoExtendMinutes
}

/**
 * §6 `failed`, grouped by task.
 *
 * [accepted] is a **contract addition, asked for and not yet served** (tracker
 * 108): a failure Joy has knowingly chosen to live with. Defaulting to false
 * means a gateway that has not shipped it behaves exactly as today.
 *
 * It is carried rather than filtered on purpose. If "Charge my watch" were
 * excluded from `failing` outright, an absent row and a row that had *stopped
 * failing* would read identically — so the day it started succeeding, nothing
 * on the screen would change. The flag moves it down the list, never off it.
 */
@Serializable
data class FailingTask(
    @SerialName("task_id") val taskId: Long,
    val title: String,
    @SerialName("is_priority") val isPriority: Boolean = false,
    @SerialName("recurrence_text") val recurrenceText: String? = null,
    val firings: Int,
    val failed: Int,
    val completed: Int,
    @Serializable(LocalDateSerializer::class)
    @SerialName("last_failed_on") val lastFailedOn: LocalDate? = null,
    val accepted: Boolean = false,
)

/**
 * §6 `drifting` over tasks: completed consistently, but not near the plan.
 *
 * Completed firings only — a firing that lapsed is `failed`, and counting it
 * here would fold the two words together.
 */
@Serializable
data class DriftingTask(
    @SerialName("task_id") val taskId: Long,
    val title: String,
    @SerialName("is_priority") val isPriority: Boolean = false,
    @SerialName("firings_measured") val firingsMeasured: Int,
    @SerialName("median_slip_minutes") val medianSlipMinutes: Int,
    @SerialName("max_slip_minutes") val maxSlipMinutes: Int,
)

/**
 * §6 `missed`: a tracked routine slot never started.
 *
 * [daysMeasured] is per-row and is not decoration — it is the denominator. A
 * bare "3" invites reading it against [RoutineProvenance.daysElapsed], which is
 * the wrong number, so the row always renders "3 of N measured days".
 */
@Serializable
data class MissedSlot(
    @SerialName("slot_key") val slotKey: String,
    val label: String,
    @SerialName("category_key") val categoryKey: String,
    @SerialName("days_measured") val daysMeasured: Int,
    val missed: Int,
    @Serializable(LocalDateSerializer::class)
    @SerialName("last_missed_on") val lastMissedOn: LocalDate? = null,
)

/**
 * §6 `drifting` for routines: started consistently, but not near plan.
 *
 * Absolute deviation, so starting early counts too — matching the app's own
 * `SlotRow.drifted`, which is `|actual - planned| > tolerance`.
 */
@Serializable
data class DriftingSlot(
    @SerialName("slot_key") val slotKey: String,
    val label: String,
    @SerialName("category_key") val categoryKey: String,
    @SerialName("starts_measured") val startsMeasured: Int,
    @SerialName("median_drift_minutes") val medianDriftMinutes: Int,
    @SerialName("max_drift_minutes") val maxDriftMinutes: Int,
)

/** §6 `stale`: an undated to-do untouched past a threshold. Cannot fail. */
@Serializable
data class StaleTodo(
    @SerialName("todo_id") val todoId: Long,
    val title: String,
    val tags: List<String> = emptyList(),
    val status: TodoStatus,
    @Serializable(InstantSerializer::class)
    @SerialName("updated_at") val updatedAt: Instant,
    @SerialName("days_untouched") val daysUntouched: Int,
)

/**
 * To-do lifecycle. Four values, shipped in migration 0009.
 *
 * **`blocked` is deliberately absent** (v2 plan §5.3, ruled 2026-09-01). These
 * four answer *where an item is*; "blocked" answers *why it is not moving*,
 * which is a different question — and putting the second answer in this field
 * destroys the first, because `doing → blocked → unblocked` cannot say which
 * value to return to. If it earns its place it arrives as a separate
 * `blocked_on`, orthogonal to this.
 *
 * Not to be confused with [TaskStatus], which is about firing and chasing.
 */
@Serializable
enum class TodoStatus {
    @SerialName("open") Open,
    @SerialName("doing") Doing,
    @SerialName("done") Done,
    @SerialName("cancelled") Cancelled,
}
