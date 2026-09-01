package com.ar13x.jarvis.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ar13x.jarvis.core.data.message
import com.ar13x.jarvis.core.model.DriftingSlot
import com.ar13x.jarvis.core.model.DriftingTask
import com.ar13x.jarvis.core.model.FailingTask
import com.ar13x.jarvis.core.model.MissedSlot
import com.ar13x.jarvis.core.model.StaleTodo
import com.ar13x.jarvis.core.ui.LoadState
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.component.SectionHeader
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.designsystem.theme.tabularNums
import java.time.format.DateTimeFormatter

/**
 * How it is going (v2 plan §6).
 *
 * **The gateway computes every number on this screen.** Nothing here buckets a
 * day, averages a slip or works out a streak — all of that arrives finished, and
 * the app's whole job is to lay it out and to be honest about what it does not
 * know.
 *
 * That second half is the reason the screen is shaped the way it is. Four of its
 * sections can be empty for two entirely different reasons — nothing is wrong,
 * and nothing was measured — and the reassuring reading is the one a plain empty
 * state gives for free. So no section renders an empty state on its own
 * authority: each asks [DashboardView] for its [Evidence] first, and the
 * `Incomplete` note wins.
 */
@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = JarvisTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        Header(onBack = onBack)

        when (val s = state) {
            is LoadState.Loading -> Centred { CircularProgressIndicator(color = colors.brandCore) }

            is LoadState.Failed -> Centred {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = s.reason.message(),
                        style = JarvisTheme.typography.bodyMedium,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Space.x8),
                    )
                    // No partial dashboard on failure. Half of it is
                    // indistinguishable from a quiet week.
                    TextButton(onClick = viewModel::load) { Text("Try again") }
                }
            }

            is LoadState.Ready -> Content(s.data)
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    val colors = JarvisTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.Gutter, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(onClick = onBack, diameter = 36.dp) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = colors.inkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(Space.x3))
        Text(
            text = "How it is going",
            style = JarvisTheme.typography.titleLarge,
            color = colors.ink,
        )
    }
}

@Composable
private fun Content(view: DashboardView) {
    val d = view.dashboard

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.x12),
    ) {
        item { Window(view) }
        item { Totals(view) }

        // --- failing ----------------------------------------------------------
        //
        // `failed` is the one section with no provenance caveat, and that is a
        // fact about the data rather than an oversight: `status` is stored per
        // firing and nothing overwrites it, so this half survives in the
        // projection even for firings the event stream predates.
        item { SectionHeader("Not completed") }
        item {
            Caption(
                // Names the mechanism, because "failed" invites reading it as a
                // judgement. It ran out of chances; cancelling is a different
                // thing and is counted separately above.
                "Ran out of extensions without being answered. Cancelling is not counted here.",
            )
        }
        if (view.failing.isEmpty() && view.acceptedFailures.isEmpty()) {
            item { Empty("Nothing has run out of extensions in this window.") }
        }
        items(view.failing, key = { "fail-" + it.taskId }) { FailingRow(it, accepted = false) }

        if (view.acceptedFailures.isNotEmpty()) {
            // Sunk, not hidden. Leading with something Joy chose to live with
            // teaches her to skip the top row; dropping it means the day it
            // stops failing looks like every day before it.
            item { SectionHeader("Accepted") }
            item {
                Caption("Known and chosen. Still counted, just not shouted about.")
            }
            items(view.acceptedFailures, key = { "acc-" + it.taskId }) {
                FailingRow(it, accepted = true)
            }
        }

        // --- task drift -------------------------------------------------------
        item { SectionHeader("Drifting") }
        item { Caption("Finished, but more than " + d.policy.driftThresholdMinutes + " minutes past the deadline.") }
        when (val evidence = view.taskDriftEvidence) {
            is Evidence.Incomplete -> item { ProvenanceNote(evidence.note) }
            Evidence.Measured -> if (d.drifting.isEmpty()) {
                item { Empty("Nothing is drifting.") }
            }
        }
        items(d.drifting, key = { "drift-" + it.taskId }) { DriftingRow(it) }

        // --- routine ----------------------------------------------------------
        item { SectionHeader("Routine") }
        when (val evidence = view.routineEvidence) {
            is Evidence.Incomplete -> item { ProvenanceNote(evidence.note) }
            Evidence.Measured -> if (d.missed.isEmpty() && d.driftingSlots.isEmpty()) {
                item { Empty("Every tracked slot was started, near plan.") }
            }
        }
        items(d.missed, key = { "missed-" + it.slotKey }) { MissedRow(it, view) }
        items(d.driftingSlots, key = { "dslot-" + it.slotKey }) {
            DriftingSlotRow(it, d.policy.routineDriftThresholdMinutes)
        }

        // --- stale ------------------------------------------------------------
        item { SectionHeader("Stale") }
        view.staleCaption?.let { item { ProvenanceNote(it) } }
        item { Caption("Undated to-dos nothing has moved in a fortnight.") }
        if (d.stale.isEmpty()) {
            // The one section whose empty state needs no caveat: `stale` is
            // computed from now and from `updated_at`, both of which are always
            // available. There is no window in which it cannot see.
            item { Empty("Nothing has gone quiet.") }
        }
        items(d.stale, key = { "stale-" + it.todoId }) { StaleRow(it) }
    }
}

// --- header blocks -------------------------------------------------------------

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

@Composable
private fun Window(view: DashboardView) {
    val period = view.dashboard.period
    Caption(
        period.dateFrom.format(DAY) + " – " + period.dateTo.format(DAY) +
            // Stated rather than assumed: §3 settled that "what did I miss" is
            // the day something was due, and the app must not re-bucket.
            " · bucketed by " + period.bucketedBy + " date",
    )
}

@Composable
private fun Totals(view: DashboardView) {
    val t = view.dashboard.totals
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x2),
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Stat("Firings", t.firings, Modifier.weight(1f))
        Stat("Done", t.completed, Modifier.weight(1f))
        // "Not done" and "Cancelled" are separate columns because they are
        // separate facts. `cancelled` was on the wire and simply not rendered,
        // which folded a decision you made into a count of things that went
        // wrong -- and made the failure figure look worse than it is.
        Stat("Not done", t.failed, Modifier.weight(1f))
        Stat("Cancelled", t.cancelled, Modifier.weight(1f))
        Stat("Open", t.unresolved, Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    val colors = JarvisTheme.colors
    Column(
        modifier
            .clip(Corner.Sm)
            .background(colors.surfaceSunk)
            .padding(vertical = Space.x3, horizontal = Space.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value.toString(),
            style = JarvisTheme.typography.titleLarge.tabularNums(),
            color = colors.ink,
        )
        Text(label, style = JarvisTheme.typography.labelSmall, color = colors.inkMuted)
    }
}

// --- rows ----------------------------------------------------------------------

@Composable
private fun FailingRow(task: FailingTask, accepted: Boolean) {
    val colors = JarvisTheme.colors
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = task.title,
                style = JarvisTheme.typography.titleMedium,
                // Accepted rows are quieter, never absent. The numbers on them
                // are the same numbers.
                color = if (accepted) colors.inkMuted else colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = task.failed.toString() + " of " + task.firings,
                style = JarvisTheme.typography.titleMedium.tabularNums(),
                color = if (accepted) colors.inkMuted else colors.status.incomplete,
            )
        }
        task.recurrenceText?.let {
            Spacer(Modifier.height(Space.x1))
            Text(it, style = JarvisTheme.typography.bodySmall, color = colors.inkMuted)
        }
        task.lastFailedOn?.let {
            Spacer(Modifier.height(Space.x1))
            Text(
                "Last on " + it.format(DAY),
                style = JarvisTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun DriftingRow(task: DriftingTask) {
    val colors = JarvisTheme.colors
    Card {
        Text(task.title, style = JarvisTheme.typography.titleMedium, color = colors.ink)
        Spacer(Modifier.height(Space.x1))
        Text(
            // Median first: one very late finish should not describe the habit.
            // Both are on the wire so the outlier is still visible.
            text = "Usually " + minutes(task.medianSlipMinutes) + " late, worst " +
                minutes(task.maxSlipMinutes) + " · over " + task.firingsMeasured + " finishes",
            style = JarvisTheme.typography.bodySmall,
            color = colors.inkMuted,
        )
    }
}

@Composable
private fun MissedRow(slot: MissedSlot, view: DashboardView) {
    val colors = JarvisTheme.colors
    Card {
        Text(slot.label, style = JarvisTheme.typography.titleMedium, color = colors.ink)
        Spacer(Modifier.height(Space.x1))
        Text(
            // Always with its denominator. A bare "3" invites reading it against
            // days_elapsed, which is a different and larger number.
            text = view.missedLine(slot.missed, slot.daysMeasured),
            style = JarvisTheme.typography.bodySmall,
            color = colors.inkMuted,
        )
    }
}

@Composable
private fun DriftingSlotRow(slot: DriftingSlot, thresholdMinutes: Int) {
    val colors = JarvisTheme.colors
    Card {
        Text(slot.label, style = JarvisTheme.typography.titleMedium, color = colors.ink)
        Spacer(Modifier.height(Space.x1))
        Text(
            // "off plan", not "late": the deviation is absolute, so an early
            // start counts too, and calling it lateness would misreport half of
            // them.
            text = "Usually " + minutes(slot.medianDriftMinutes) + " off plan, worst " +
                minutes(slot.maxDriftMinutes) + " · over " + slot.startsMeasured +
                " starts, past " + thresholdMinutes + " min",
            style = JarvisTheme.typography.bodySmall,
            color = colors.inkMuted,
        )
    }
}

@Composable
private fun StaleRow(todo: StaleTodo) {
    val colors = JarvisTheme.colors
    Card {
        Text(todo.title, style = JarvisTheme.typography.titleMedium, color = colors.ink)
        Spacer(Modifier.height(Space.x1))
        Text(
            text = "Untouched " + todo.daysUntouched + " days" +
                if (todo.tags.isEmpty()) "" else " · " + todo.tags.joinToString(", "),
            style = JarvisTheme.typography.bodySmall,
            color = colors.inkMuted,
        )
    }
}

// --- small pieces --------------------------------------------------------------

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    JarvisCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x1),
        content = content,
    )
}

/**
 * The one piece of copy on this screen that must never be mistaken for a
 * result.
 *
 * Given its own treatment rather than reusing [Caption] on purpose: a caveat
 * that looks like the explanatory line above a section is a caveat nobody
 * separates from decoration. This one is saying the numbers beside it cannot be
 * trusted yet, which is a different kind of sentence.
 */
@Composable
private fun ProvenanceNote(text: String) {
    val colors = JarvisTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x1)
            .clip(Corner.Sm)
            .background(colors.brandTint)
            .padding(Space.x3),
    ) {
        Text(text, style = JarvisTheme.typography.bodySmall, color = colors.brandDeep)
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = JarvisTheme.typography.bodySmall,
        color = JarvisTheme.colors.inkMuted,
        modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.x1),
    )
}

@Composable
private fun Empty(text: String) {
    Text(
        text = text,
        style = JarvisTheme.typography.bodyMedium,
        color = JarvisTheme.colors.inkMuted,
        modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.x3),
    )
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** "1 h 15 m" reads better than "75 minutes" once slips get long. */
private fun minutes(total: Int): String = when {
    total < 60 -> total.toString() + " min"
    total % 60 == 0 -> (total / 60).toString() + " h"
    else -> (total / 60).toString() + " h " + (total % 60) + " m"
}
