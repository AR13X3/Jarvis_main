package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * One stored summary (v2 plan §7).
 *
 * **Two things, and only one of them is generated.** [facts] is counted by the
 * gateway and is as trustworthy as the dashboard; [text] is written by a model
 * *from* those facts and is an interpretation of them. Both are on the wire so a
 * reader can hold the sentence against the arithmetic it came from — and so the
 * app can render the numbers when the prose is missing, which is what a
 * `facts_only` generation produces and what a model failure leaves behind.
 *
 * The screen keeps them visually separate for that reason. A summary whose prose
 * and whose counts disagree is a thing worth being able to notice.
 */
@Serializable
data class Summary(
    @SerialName("summary_id") val summaryId: Long,
    val period: SummaryPeriod,
    /** The group this covers, or `null` for everything. */
    val tag: String? = null,
    @Serializable(LocalDateSerializer::class)
    @SerialName("period_start") val periodStart: LocalDate,
    @Serializable(LocalDateSerializer::class)
    @SerialName("period_end") val periodEnd: LocalDate,
    val facts: SummaryFacts,
    /** The model's sentences. Absent when generated facts-only, or when it failed. */
    val text: String? = null,
    /** Which model wrote [text]. Shown, because "an AI wrote this" is not enough. */
    val model: String? = null,
    @Serializable(InstantSerializer::class)
    @SerialName("generated_at") val generatedAt: Instant,
)

@Serializable
data class Summaries(val summaries: List<Summary> = emptyList())

@Serializable
enum class SummaryPeriod {
    @SerialName("daily") Daily,
    @SerialName("weekly") Weekly,
}

/**
 * The counted half of a summary, and the trustworthy one.
 *
 * ### The maps are keyed by `String`, deliberately
 *
 * gw03 published the legal keys as named enums in tracker 110 precisely so the
 * app would stop guessing them, and [SummaryReminderVerb] / [SummaryTodoVerb]
 * are those enums. They are still not used as the map's key *type*, because a
 * key the app does not recognise would then throw and take the whole summaries
 * screen down — `coerceInputValues` does not apply to map keys, and there is no
 * default to coerce to.
 *
 * So the wire keys are kept verbatim and read through [reminders] / [todos].
 * This is the same trade as `TodoEvent.verb`, for the same reason: the gateway
 * being one deploy ahead of the app must never be fatal.
 *
 * ### A KEY IS ABSENT RATHER THAN ZERO
 *
 * This is the sentence the whole vocabulary ask existed for. When nothing of a
 * kind happened, its key is simply not there. That means **a misspelled lookup
 * and a genuinely quiet day produce the same zero**, and only one of them is
 * true — which is why the spellings are asserted against the served contract in
 * `SummaryContractTest` rather than typed from memory here.
 */
@Serializable
data class SummaryFacts(
    val period: SummaryPeriod,
    @Serializable(LocalDateSerializer::class)
    @SerialName("period_start") val periodStart: LocalDate,
    @Serializable(LocalDateSerializer::class)
    @SerialName("period_end") val periodEnd: LocalDate,
    val tag: String? = null,
    /** Keyed by [SummaryReminderVerb]'s wire spellings. Absent key means none. */
    val reminders: Map<String, SummaryCount> = emptyMap(),
    val routine: SummaryRoutineFacts? = null,
    /** Keyed by [SummaryTodoVerb]'s wire spellings. Absent key means none. */
    val todos: Map<String, SummaryCount> = emptyMap(),
    /**
     * The day the event stream began. Anything before it is reconstruction.
     *
     * The dashboard carries the same idea as `provenance`, and for the same
     * reason: presenting a reconstructed number as a measurement is the failure
     * this project has been most careful about.
     */
    @Serializable(LocalDateSerializer::class)
    @SerialName("records_began_on") val recordsBeganOn: LocalDate? = null,
    /**
     * True when this period ends before records began.
     *
     * **Every count below is then a floor rather than a total**, and the screen
     * has to say so. A quiet week and an unrecorded week look identical.
     */
    @SerialName("period_predates_records") val periodPredatesRecords: Boolean = false,
    /** False when the period is genuinely empty — as opposed to unrecorded. */
    @SerialName("anything_happened") val anythingHappened: Boolean = false,
) {
    /** `null` when nothing of that kind happened — which is what absent means. */
    fun reminders(verb: SummaryReminderVerb): SummaryCount? = reminders[verb.wire]

    fun todos(verb: SummaryTodoVerb): SummaryCount? = todos[verb.wire]

    /**
     * Keys the gateway sent that this build has no word for.
     *
     * Rendered rather than dropped, under their raw names. An eleventh verb is
     * the gateway being ahead, and a count silently disappearing off a summary
     * is exactly the wrong-zero this whole design is about.
     */
    val unknownReminderKeys: List<String>
        get() = reminders.keys.filter { key -> SummaryReminderVerb.of(key) == null }

    val unknownTodoKeys: List<String>
        get() = todos.keys.filter { key -> SummaryTodoVerb.of(key) == null }
}

/**
 * How many, and which ones.
 *
 * Names as well as a count because §7's summaries are about *what you did*, and
 * "3 reminders completed" is a worse sentence than the three titles.
 */
@Serializable
data class SummaryCount(
    val count: Int,
    val titles: List<String> = emptyList(),
)

@Serializable
data class SummaryRoutineFacts(
    @SerialName("slots_started") val slotsStarted: List<SummarySlotStarted> = emptyList(),
    /**
     * How many days in the period carry any start at all.
     *
     * Load-bearing for reading the rest: adherence over a week where only two
     * days were logged is a fact about the logging, not about the week.
     */
    @SerialName("days_with_any_logging") val daysWithAnyLogging: Int = 0,
)

@Serializable
data class SummarySlotStarted(
    val label: String,
    val category: String,
    val times: Int,
    /**
     * At least one of these starts was recorded after its logical day had ended.
     *
     * The overrun signal — a run of Speedway nights finishing at 3am. Kept
     * rather than smoothed away, so it is worth showing rather than hiding.
     */
    @SerialName("any_after_the_day_ended") val anyAfterTheDayEnded: Boolean = false,
)

/**
 * The reminder verbs a summary counts — a strict subset of the full vocabulary.
 *
 * §7 summarises what *you* did, so the machinery verbs are left out: `asked`,
 * `extended` and `revived` are the loop chasing you rather than an outcome.
 *
 * **Read `cancelled` narrowly.** It is the `cancelled` event alone — a firing
 * somebody decided against. A reminder whose deadline *moved* is `superseded`, a
 * different word, and it is not counted here at all. So this number is decisions
 * and never reschedules, which is the distinction that makes it worth showing.
 */
enum class SummaryReminderVerb(val wire: String) {
    Completed("completed"),
    Lapsed("lapsed"),
    Cancelled("cancelled"),
    ;

    companion object {
        private val byWire = entries.associateBy { it.wire }
        fun of(wire: String): SummaryReminderVerb? = byWire[wire]
    }
}

/**
 * The to-do verbs a summary counts.
 *
 * The edit verbs — `moved`, `renamed`, `tagged`, `untagged`, `linked`,
 * `unlinked`, `reopened` — are churn rather than outcomes and are left out.
 */
enum class SummaryTodoVerb(val wire: String) {
    Created("created"),
    Done("done"),
    Cancelled("cancelled"),
    ;

    companion object {
        private val byWire = entries.associateBy { it.wire }
        fun of(wire: String): SummaryTodoVerb? = byWire[wire]
    }
}
