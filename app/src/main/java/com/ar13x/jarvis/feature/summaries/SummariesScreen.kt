package com.ar13x.jarvis.feature.summaries

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ar13x.jarvis.core.data.message
import com.ar13x.jarvis.core.model.Summary
import com.ar13x.jarvis.core.model.SummaryCount
import com.ar13x.jarvis.core.model.SummaryPeriod
import com.ar13x.jarvis.core.model.SummaryReminderVerb
import com.ar13x.jarvis.core.model.SummaryTodoVerb
import com.ar13x.jarvis.core.ui.LoadState
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.component.JarvisChip
import com.ar13x.jarvis.designsystem.component.SectionHeader
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import java.time.format.DateTimeFormatter

/**
 * The written record (v2 plan §7).
 *
 * **Facts and prose are kept visually apart, and that is the whole design.** The
 * gateway counts `facts`; a model writes `text` *from* those facts. One of those
 * is arithmetic and the other is an interpretation, and a screen that blended
 * them would make the interpretation look as solid as the count. Both are on the
 * wire precisely so a reader can hold the sentence against the numbers it came
 * from — so both are shown, labelled, and the numbers are shown even when the
 * prose is missing.
 *
 * **A missing count is not a zero on this screen either.** A key absent from
 * `facts.reminders` means nothing of that kind happened, so the row is left out
 * rather than drawn as "0" — but a period that *predates the records* has every
 * count as a floor rather than a total, and that says so at the top. A quiet
 * week and an unrecorded week look identical otherwise, which is the wrong-zero
 * this whole feature was held back over.
 */
@Composable
fun SummariesScreen(
    onBack: () -> Unit,
    viewModel: SummariesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = JarvisTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        Header(onBack)
        Filters(state, viewModel)

        state.transientFailure?.let { reason ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.Gutter, vertical = Space.x1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    reason.message(),
                    style = JarvisTheme.typography.bodySmall,
                    color = colors.status.incomplete,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::dismissFailure) { Text("Dismiss") }
            }
        }

        when (val content = state.content) {
            is LoadState.Loading -> Centred { CircularProgressIndicator(color = colors.brandCore) }

            is LoadState.Failed -> Centred {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        content.reason.message(),
                        style = JarvisTheme.typography.bodyMedium,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Space.x8),
                    )
                    // No cached summaries to fall back on, and deliberately so:
                    // there is no fake and no local store, because inventing
                    // sentences about invented arithmetic is the one fiction
                    // this screen must not be able to produce.
                    TextButton(onClick = viewModel::load) { Text("Try again") }
                }
            }

            is LoadState.Ready -> if (content.data.isEmpty()) {
                Centred {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = Space.x8),
                    ) {
                        Text(
                            "No summaries yet.",
                            style = JarvisTheme.typography.bodyMedium,
                            color = colors.inkMuted,
                        )
                        Spacer(Modifier.height(Space.x1))
                        Text(
                            "The gateway writes one each morning. You can also ask for one now.",
                            style = JarvisTheme.typography.bodySmall,
                            color = colors.inkMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = Space.x12)) {
                    items(content.data, key = { it.summaryId }) { summary ->
                        SummaryCard(summary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(state: SummariesUiState, viewModel: SummariesViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x2),
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (period in SummaryPeriod.entries) {
            JarvisChip(
                label = period.label,
                selected = state.period == period,
                onClick = { viewModel.selectPeriod(period) },
            )
        }
        Spacer(Modifier.weight(1f))
        // Costs a model call, so it says what it does rather than being a
        // refresh icon. gw03 already writes one every morning unprompted; this
        // is for asking again after something changed.
        TextButton(
            enabled = !state.generating,
            onClick = { viewModel.generate(state.period ?: SummaryPeriod.Daily) },
        ) {
            Text(if (state.generating) "Writing…" else "Write one now")
        }
    }
}

@Composable
private fun SummaryCard(summary: Summary) {
    val colors = JarvisTheme.colors
    val facts = summary.facts

    JarvisCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x1),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = summary.range(),
                style = JarvisTheme.typography.titleMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            summary.tag?.let {
                Text(it, style = JarvisTheme.typography.labelMedium, color = colors.inkMuted)
            }
        }

        // Provenance before anything else, exactly as the dashboard does it. A
        // number presented as a measurement when it is a floor is the failure
        // this project has been most careful about.
        if (facts.periodPredatesRecords) {
            Spacer(Modifier.height(Space.x2))
            Text(
                text = "This period ends before the records begin" +
                    (facts.recordsBeganOn?.let { " (" + it.format(DAY) + ")" } ?: "") +
                    ". Every count below is a floor, not a total.",
                style = JarvisTheme.typography.bodySmall,
                color = colors.status.incomplete,
            )
        }

        if (!facts.anythingHappened && !facts.periodPredatesRecords) {
            Spacer(Modifier.height(Space.x2))
            Text(
                // Says which emptiness it is. This one is measured: the records
                // covered the period and there was nothing in it.
                "Nothing was recorded in this period.",
                style = JarvisTheme.typography.bodyMedium,
                color = colors.inkMuted,
            )
        }

        // --- the prose, labelled as prose ------------------------------------
        summary.text?.let { text ->
            Spacer(Modifier.height(Space.x3))
            Text(text, style = JarvisTheme.typography.bodyMedium, color = colors.ink)
            Spacer(Modifier.height(Space.x1))
            Text(
                // Named, because "an AI wrote this" is not enough to judge it by
                // and because the facts underneath are a different kind of thing.
                text = "Written by " + (summary.model ?: "a model") + " from the counts below.",
                style = JarvisTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        }

        // --- the counts, which are the trustworthy half ----------------------
        val reminderRows = SummaryReminderVerb.entries.mapNotNull { verb ->
            facts.reminders(verb)?.let { verb.label to it }
        } + facts.unknownReminderKeys.map { key -> key to facts.reminders.getValue(key) }

        val todoRows = SummaryTodoVerb.entries.mapNotNull { verb ->
            facts.todos(verb)?.let { verb.label to it }
        } + facts.unknownTodoKeys.map { key -> key to facts.todos.getValue(key) }

        if (reminderRows.isNotEmpty()) {
            CountBlock("Reminders", reminderRows)
        }
        if (todoRows.isNotEmpty()) {
            CountBlock("To-dos", todoRows)
        }

        facts.routine?.takeIf { it.slotsStarted.isNotEmpty() }?.let { routine ->
            Spacer(Modifier.height(Space.x3))
            Text(
                "Routine",
                style = JarvisTheme.typography.labelSmall,
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(Space.x1))
            Text(
                // The denominator, and it is load-bearing: adherence over a week
                // where only two days were logged is a fact about the logging.
                text = "Logged on " + routine.daysWithAnyLogging +
                    (if (routine.daysWithAnyLogging == 1) " day" else " days"),
                style = JarvisTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
            for (slot in routine.slotsStarted) {
                Spacer(Modifier.height(Space.x1))
                Text(
                    text = slot.label + " · " + slot.times +
                        (if (slot.times == 1) " time" else " times") +
                        // The overrun signal, kept rather than smoothed away.
                        (if (slot.anyAfterTheDayEnded) " · some after the day ended" else ""),
                    style = JarvisTheme.typography.bodySmall,
                    color = colors.ink,
                )
            }
        }
    }
}

@Composable
private fun CountBlock(title: String, rows: List<Pair<String, SummaryCount>>) {
    val colors = JarvisTheme.colors

    Spacer(Modifier.height(Space.x3))
    Text(title, style = JarvisTheme.typography.labelSmall, color = colors.inkMuted)

    for ((label, count) in rows) {
        Spacer(Modifier.height(Space.x1))
        Text(
            text = count.count.toString() + " " + label,
            style = JarvisTheme.typography.bodyMedium,
            color = colors.ink,
        )
        if (count.titles.isNotEmpty()) {
            Text(
                // The titles, not just the number: §7's summaries are about what
                // you did, and "3 reminders completed" is a worse sentence than
                // the three names.
                text = count.titles.joinToString(" · "),
                style = JarvisTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
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
        Text("Summaries", style = JarvisTheme.typography.titleLarge, color = colors.ink)
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private fun Summary.range(): String = when (period) {
    // A daily summary's start and end are the same day, and printing it twice
    // reads as a bug rather than as a range.
    SummaryPeriod.Daily -> periodStart.format(DAY)
    SummaryPeriod.Weekly -> periodStart.format(DAY) + " – " + periodEnd.format(DAY)
}

private val SummaryPeriod.label: String
    get() = when (this) {
        SummaryPeriod.Daily -> "Daily"
        SummaryPeriod.Weekly -> "Weekly"
    }

private val SummaryReminderVerb.label: String
    get() = when (this) {
        SummaryReminderVerb.Completed -> "completed"
        SummaryReminderVerb.Lapsed -> "lapsed"
        // Narrow, and the word matters: this is a firing somebody decided
        // against. A reminder whose deadline MOVED is `superseded` and is not
        // counted here at all, so this number is decisions and never reschedules.
        SummaryReminderVerb.Cancelled -> "cancelled"
    }

private val SummaryTodoVerb.label: String
    get() = when (this) {
        SummaryTodoVerb.Created -> "created"
        SummaryTodoVerb.Done -> "done"
        SummaryTodoVerb.Cancelled -> "cancelled"
    }

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
