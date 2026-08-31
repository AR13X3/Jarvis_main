package com.ar13x.jarvis.feature.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ar13x.jarvis.core.model.CategoryClass
import com.ar13x.jarvis.core.model.Routine
import com.ar13x.jarvis.core.model.RoutineDay
import com.ar13x.jarvis.core.model.durationMinutes
import com.ar13x.jarvis.core.model.weeklyMinutesByCategory
import com.ar13x.jarvis.core.model.weeklyMinutesByClass
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.designsystem.theme.routineCategoryColor
import com.ar13x.jarvis.designsystem.theme.tabularNums
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Where the hours go (v2 plan §4.1).
 *
 * The half of Joy's `the-week.html` that is not a list of things to do. It is
 * arithmetic over the *template* — deterministic, unchanging until the routine
 * is edited — and deliberately says nothing about adherence, which is the
 * gateway's to compute and is not wired yet.
 *
 * Kept as its own screen rather than folded above the day list. The day view
 * answers "what now"; this answers "where is my week going", and putting the
 * second above the first would mean scrolling past the shape of the week every
 * time you wanted to tick off the gym.
 */
@Composable
fun RoutineWeekScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routine = state.routine ?: return

    LazyColumn(modifier.fillMaxWidth()) {
        item { WeekHeader(routine, onBack) }
        item { HeadlineSplit(routine) }

        item { SectionLabel("WEEKLY TOTALS") }
        weekBody(routine)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.weekBody(routine: Routine) {
    val totals = routine.weeklyMinutesByCategory()
    val week = totals.values.sum().coerceAtLeast(1)
    val ranked = totals.entries.sortedByDescending { it.value }

    for ((id, minutes) in ranked) {
        item(key = "total-" + id) {
            TotalRow(
                label = routine.category(id)?.label ?: id,
                categoryId = id,
                hours = minutes / 60.0,
                percent = (minutes * 100.0 / week).roundToInt(),
            )
        }
    }

    item { SectionLabel("HOUR BY HOUR") }

    for (day in routine.days) {
        item(key = "day-" + day.weekday) { WeekDayRow(day) }
    }

    if (routine.notes.isNotEmpty()) {
        item { SectionLabel("HOW IT HOLDS TOGETHER") }
        for ((index, note) in routine.notes.withIndex()) {
            item(key = "note-" + index) {
                Text(
                    text = note,
                    style = JarvisTheme.typography.bodySmall,
                    color = JarvisTheme.colors.inkMuted,
                    modifier = Modifier.padding(
                        start = Space.Gutter,
                        end = Space.Gutter,
                        bottom = Space.x3,
                    ),
                )
            }
        }
    }
    item { Spacer(Modifier.height(Space.x12)) }
}

@Composable
private fun WeekHeader(routine: Routine, onBack: () -> Unit) {
    val colors = JarvisTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.Gutter, vertical = Space.x3),
    ) {
        CircleIconButton(onClick = onBack, diameter = 40.dp) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to the day",
                tint = colors.ink,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.height(Space.x4))
        Text(
            text = "Where the hours go",
            style = JarvisTheme.typography.titleLarge,
            color = colors.ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = hours(routine.weeklyMinutesByCategory().values.sum()) + " waking hours a week",
            style = JarvisTheme.typography.bodySmall.tabularNums(),
            color = colors.inkMuted,
        )
    }
}

/** Committed, upkeep, free — the three-way split from the top of Joy's file. */
@Composable
private fun HeadlineSplit(routine: Routine) {
    val byClass = routine.weeklyMinutesByClass()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.Gutter, vertical = Space.x2),
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        SplitCard("Committed", byClass[CategoryClass.Committed] ?: 0, Modifier.weight(1f))
        SplitCard("Life upkeep", byClass[CategoryClass.Upkeep] ?: 0, Modifier.weight(1f))
        SplitCard("Free, buffer", byClass[CategoryClass.Free] ?: 0, Modifier.weight(1f))
    }
}

@Composable
private fun SplitCard(label: String, minutes: Int, modifier: Modifier = Modifier) {
    val colors = JarvisTheme.colors
    Column(
        modifier
            .clip(Corner.Sm)
            .background(colors.surface, Corner.Sm)
            .padding(Space.x3),
    ) {
        Text(
            text = hours(minutes),
            style = JarvisTheme.typography.titleLarge.tabularNums(),
            color = colors.ink,
        )
        Text(
            text = label,
            style = JarvisTheme.typography.bodySmall,
            color = colors.inkMuted,
        )
    }
}

@Composable
private fun TotalRow(label: String, categoryId: String, hours: Double, percent: Int) {
    val colors = JarvisTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(Corner.Xs)
                .background(routineCategoryColor(categoryId)),
        )
        Spacer(Modifier.width(Space.x3))
        Text(
            text = label,
            style = JarvisTheme.typography.bodyLarge,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = trim(hours),
            style = JarvisTheme.typography.titleMedium.tabularNums(),
            color = colors.ink,
        )
        Spacer(Modifier.width(Space.x3))
        Text(
            text = percent.toString() + "%",
            style = JarvisTheme.typography.bodySmall.tabularNums(),
            color = colors.inkMuted,
        )
    }
}

@Composable
private fun WeekDayRow(day: RoutineDay) {
    val colors = JarvisTheme.colors
    val total = day.slots.sumOf { it.durationMinutes }.toFloat().coerceAtLeast(1f)

    Column(Modifier.fillMaxWidth().padding(horizontal = Space.Gutter, vertical = Space.x2)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = day.weekday.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                style = JarvisTheme.typography.bodyLarge,
                color = colors.ink,
            )
            day.note?.let {
                Spacer(Modifier.width(Space.x2))
                Text(it, style = JarvisTheme.typography.bodySmall, color = colors.inkMuted)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = trim(total / 60.0) + "h",
                style = JarvisTheme.typography.bodySmall.tabularNums(),
                color = colors.inkMuted,
            )
        }
        Spacer(Modifier.height(Space.x2))
        Row(
            Modifier.fillMaxWidth().height(9.dp).clip(Corner.Pill),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        ) {
            for (slot in day.slots) {
                Box(
                    Modifier
                        .weight(slot.durationMinutes / total)
                        .fillMaxHeight()
                        .background(routineCategoryColor(slot.categoryId)),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = JarvisTheme.typography.labelSmall,
        color = JarvisTheme.colors.inkMuted,
        modifier = Modifier.padding(
            start = Space.Gutter,
            end = Space.Gutter,
            top = Space.x6,
            bottom = Space.x2,
        ),
    )
}

private fun hours(minutes: Int): String = trim(minutes / 60.0)

/** 31.5 rather than 31.50, and 20 rather than 20.0 — as Joy's own file writes them. */
private fun trim(value: Double): String {
    val rounded = Math.round(value * 100) / 100.0
    return if (rounded == Math.floor(rounded)) rounded.toInt().toString()
    else rounded.toString().trimEnd('0').trimEnd('.')
}
