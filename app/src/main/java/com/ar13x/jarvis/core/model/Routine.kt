package com.ar13x.jarvis.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * A repeating weekly plan that partitions the day (v2 plan §4).
 *
 * **A routine is a budget, not a checklist.** Every minute from waking to sleep
 * is allocated, back to back, with no gaps — which is the only reason weekly
 * totals and percentages mean anything. Reminders are points in time and tasks
 * are ranges; this is the third shape, and that is why it is its own model
 * rather than tasks with a start time.
 *
 * Nothing here derives a calendar day from a timestamp. A routine day runs from
 * waking to sleeping and its boundaries are *declared* per weekday — see
 * [RoutineDay.endsAt] and `RoutineClock`.
 */
data class Routine(
    val id: String,
    val name: String,
    /**
     * The date this version starts applying.
     *
     * Editing a routine must never rewrite the past: if it did, moving gym to
     * 4pm today would silently restate three months of adherence and every
     * "you are failing at this" would become fiction. So an edit creates a new
     * version from tomorrow, and days already measured keep the plan they were
     * measured against (§4.6).
     */
    val effectiveFrom: LocalDate,
    val categories: List<RoutineCategory>,
    val days: List<RoutineDay>,
    /**
     * The operating rules — "spend Wednesday's buffer on overruns, not new
     * work", "Speedway nights give about six hours' sleep, three in a row".
     *
     * These are why the shape is what it is. Dropping them means rebuilding the
     * reasoning from scratch in three months (§4.8).
     */
    val notes: List<String> = emptyList(),
    /**
     * How far off plan a start must be to count as drift, in minutes.
     * **The gateway's number, not the app's.** Null when none has been received.
     *
     * It belongs on the routine rather than in `SlotRow` because §4.2 requires
     * the routine tab to render **off the tailnet**, from this cached object.
     * The gateway currently publishes it only as
     * `policy.routine_drift_threshold_minutes` on `GET /dashboard` —
     * authenticated, network-only, and hanging off an object the routine tab
     * never fetches — so a cold start, or a tram, would leave the tab with
     * nothing but a client-side guess. Tracker 109 asks for it on `GET /routine`
     * as well; this field is where it lands.
     *
     * Until then the fixture supplies it, which is honest in a way the constant
     * it replaces was not: inventing a number is what a fixture is *for*,
     * whereas production logic inventing a policy value is the client quietly
     * overruling the server. When the field reaches the wire this becomes a
     * plain deserialisation and nothing above it changes.
     */
    val driftToleranceMinutes: Int? = null,
) {
    fun day(weekday: DayOfWeek): RoutineDay? = days.firstOrNull { it.weekday == weekday }

    fun category(id: String): RoutineCategory? = categories.firstOrNull { it.id == id }
}

/**
 * One weekday's plan.
 *
 * [startsAt] and [endsAt] are the *logical* day — waking to sleeping — and they
 * differ per weekday: Thursday starts at 10:00 to pay down sleep debt, Friday
 * to Sunday run until 2am after a Speedway shift. When [endsAt] is earlier than
 * [startsAt] the day crosses midnight, which is the ordinary case here rather
 * than an edge one.
 */
data class RoutineDay(
    val weekday: DayOfWeek,
    val startsAt: LocalTime,
    val endsAt: LocalTime,
    val slots: List<RoutineSlot>,
    /** "buffer day", "day off" — shown beside the day name. */
    val note: String? = null,
) {
    /** True when this day runs past midnight into the next calendar date. */
    val crossesMidnight: Boolean get() = endsAt <= startsAt

    val trackedSlots: List<RoutineSlot> get() = slots.filter { it.kind == SlotKind.Tracked }
}

/**
 * One contiguous block of the day.
 *
 * [end] earlier than [start] means the slot crosses midnight — Friday's shift
 * runs 13:45 to 00:15 and two more slots follow it.
 */
data class RoutineSlot(
    val id: String,
    val start: LocalTime,
    val end: LocalTime,
    val label: String,
    val categoryId: String,
    val kind: SlotKind,
)

/**
 * What tracking a slot means — and whether it is tracked at all.
 *
 * Twelve slots a day is about eighty a week, and ticking "lunch" daily is noise
 * that buries the five that carry signal (§4.3).
 */
enum class SlotKind {
    // Four, not three. The first version had Buffer doing the work of Buffer
    // and Free at once, which put an inverted success condition on twelve slots
    // that have no success condition at all.

    /**
     * Startable and counted: the Reskill block, web dev, gym, uni, the evening
     * meetings. Roughly five a day, and the whole of the adherence data.
     */
    Tracked,

    /**
     * Wake, lunch, shower, get ready to leave. Drawn so the day reads as
     * continuous — never startable, never counted. They exist to make the
     * budget add up.
     */
    Scaffold,

    /**
     * Deliberately empty time, and **the success condition inverts**: the win
     * is having left it alone.
     *
     * True of exactly two slots in Joy's week, both on Wednesday, and the
     * footer names them: "Wednesday's 5:30–8:30 and 11–1 are buffer. Spend them
     * on overruns, not new work."
     *
     * It was on fourteen slots before gw03 counted them. Twelve of those were
     * ordinary free time, which meant the dashboard would have congratulated
     * Joy for successfully keeping Friday's wind-down empty — and diluted the
     * one buffer number that carries signal by seven to one.
     */
    Buffer,

    /**
     * Free time. Not startable, not counted, and **not scored either way**.
     *
     * The kind that was missing. "Breather", "Calls", "Wind-down", Thursday's
     * football — none of these are commitments, none are scaffolding holding
     * the budget together, and none invert. Rest is not an achievement and not
     * a failure; it is what the other three kinds are arranged around.
     */
    Free,
}

/**
 * Speedway, Reskill, Uni, web dev, gym, life, free.
 *
 * Deliberately **not** the same vocabulary as task tags. Summaries and groups
 * belong to tasks; the routine exists to show where the hours go (§4.5). The
 * consequence, recorded rather than argued: "20h on Reskill this week" and
 * "9 Reskill tasks done" cannot share a dashboard row.
 */
data class RoutineCategory(
    val id: String,
    val label: String,
    /** Rolls up to the committed / upkeep / free headline split. */
    val cls: CategoryClass,
)

enum class CategoryClass { Committed, Upkeep, Free }
