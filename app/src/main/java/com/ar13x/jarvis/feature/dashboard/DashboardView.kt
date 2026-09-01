package com.ar13x.jarvis.feature.dashboard

import com.ar13x.jarvis.core.model.Dashboard
import com.ar13x.jarvis.core.model.FailingTask
import java.time.LocalDate

/**
 * Whether a section's silence can be read as a verdict.
 *
 * This is the whole argument of v2 plan §6, made into a type so the screen
 * cannot skip it. Four of the dashboard's sections can be empty for two
 * completely different reasons — *nothing is wrong* and *nothing was measured* —
 * and only one of those deserves a reassuring empty state.
 *
 * The failure mode is silent, it lasts about a week, and it errs in the
 * direction that reassures. So the answer is a sum type rather than a nullable
 * string: [Measured] is the only value that permits an empty state, and it has
 * to be produced deliberately.
 */
sealed interface Evidence {

    /** The record covers the period. An empty list here really does mean nothing. */
    data object Measured : Evidence

    /**
     * The record cannot speak for the period. An empty list here means nothing
     * was seen, and rendering it as a clean week would be a lie.
     */
    data class Incomplete(val note: String) : Evidence

    val isMeasured: Boolean get() = this is Measured
}

/**
 * The dashboard, arranged for rendering. Pure — given a [Dashboard] and a date
 * it is entirely determined, which is what makes the rules above testable
 * without a Hilt graph, a coroutine or a screenshot.
 *
 * **Nothing here computes a number.** Every count, median and threshold comes
 * off the wire (§6). What this adds is ordering, grouping and the four
 * provenance judgements — which are presentation decisions, and therefore the
 * app's.
 */
data class DashboardView(
    val dashboard: Dashboard,
    val today: LocalDate,
) {

    // --- failing ---------------------------------------------------------------

    /**
     * Failures Joy has not already decided to live with. These lead the screen.
     */
    val failing: List<FailingTask> get() = dashboard.failing.filterNot { it.accepted }

    /**
     * Failures marked as knowingly accepted, kept below the rest.
     *
     * Sunk rather than dropped. "Charge my watch" at the top of the list every
     * day teaches you to skip the top row; removing it altogether means the day
     * it *stops* failing looks exactly like every day before, because an absent
     * row and a fixed one are the same absence. Neither is acceptable, so it
     * moves down and keeps its real numbers.
     *
     * Empty until gw03 ships the flag (tracker 108), at which point this fills
     * in with no further change here.
     */
    val acceptedFailures: List<FailingTask> get() = dashboard.failing.filter { it.accepted }

    // --- the four provenance judgements ----------------------------------------

    /**
     * Whether `drifting` over tasks can be read.
     *
     * `firings_without_history` counts firings the event stream predates. On the
     * day the route shipped that was 27, and `drifting` was consequently empty —
     * an empty list that meant "we cannot see" and would have rendered as
     * "nothing is drifting".
     */
    val taskDriftEvidence: Evidence
        get() = if (dashboard.provenance.incomplete) {
            val n = dashboard.provenance.firingsWithoutHistory
            Evidence.Incomplete(
                "Not enough history yet — " + count(n, "firing") +
                    (if (n == 1) " predates" else " predate") + " the record.",
            )
        } else {
            Evidence.Measured
        }

    /**
     * Whether the routine numbers — `missed` and `drifting_slots` — can be read.
     *
     * Two distinct silences, and they need different sentences. Nothing measured
     * at all is not a clean week; some days measured is a real result with a
     * caveat that must be shown *every* time, not only when the gap is wide. A
     * caveat that appears above some threshold teaches the reader that its
     * absence means zero.
     */
    val routineEvidence: Evidence
        get() {
            val provenance = dashboard.routineProvenance
            return when {
                provenance.measuredNothing -> Evidence.Incomplete(
                    "Not measured yet — no routine day has been logged, so there is " +
                        "nothing to miss or drift from.",
                )

                provenance.hasGap -> Evidence.Incomplete(
                    "Measured on " + provenance.daysMeasured + " of " +
                        provenance.daysElapsed + " days. Days with no logging at all " +
                        "are left out rather than counted as missed.",
                )

                else -> Evidence.Measured
            }
        }

    /**
     * The caption the `stale` section carries, or null when it would say nothing.
     *
     * `stale` deliberately ignores the window (§5.2) — "untouched for fourteen
     * days" is a fact about now, and windowing it would let a historical
     * dashboard claim things went stale in a month that had not happened yet.
     * The cost is that in a historical window this is the one section still
     * moving, which reads as a bug unless it is labelled. When the window ends
     * today the label is omitted, because then it is saying nothing.
     */
    val staleCaption: String?
        get() = if (dashboard.period.isHistorical(today)) {
            "Always current — stale is measured from today, not from the window above."
        } else {
            null
        }

    /**
     * The line a `missed` row shows, with its denominator attached.
     *
     * Never a bare count. `MissedSlot.days_measured` is per-row on the wire
     * precisely so the row can say it, and a bare "3" invites reading it against
     * `days_elapsed` — which is a different, larger number and the wrong one.
     */
    fun missedLine(missed: Int, daysMeasured: Int): String =
        "Missed " + missed + " of " + count(daysMeasured, "measured day")
}

private fun count(n: Int, noun: String): String =
    n.toString() + " " + noun + if (n == 1) "" else "s"
