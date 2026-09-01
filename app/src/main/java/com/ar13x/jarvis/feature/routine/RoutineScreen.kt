package com.ar13x.jarvis.feature.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ar13x.jarvis.core.model.RoutineDay
import com.ar13x.jarvis.core.model.SlotKind
import com.ar13x.jarvis.core.model.durationMinutes
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.designsystem.theme.routineCategoryColor
import com.ar13x.jarvis.designsystem.theme.tabularNums
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The routine, one day at a time (v2 plan §4.9).
 *
 * Three things Joy asked for, and each is structural rather than a setting:
 * **upcoming first**, so the day is entered at the present moment and not at
 * breakfast; **one day at a time**, because scrolling a whole week to find
 * Thursday is what the HTML made tedious; and **tickable**, but only on the
 * five or so slots a day that carry signal.
 */
@Composable
fun RoutineScreen(
    onOpenWeek: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = JarvisTheme.colors
    var pastExpanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        DayPager(
            selected = state.selected,
            today = state.day?.logical?.day?.weekday.takeIf { state.day?.isToday == true },
            onSelect = viewModel::select,
        )

        val day = state.day
        if (day == null) {
            Box(Modifier.fillMaxWidth().padding(Space.x8), contentAlignment = Alignment.Center) {
                Text(
                    "No routine for this day.",
                    style = JarvisTheme.typography.bodyMedium,
                    color = colors.inkMuted,
                )
            }
            return@Column
        }

        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.x12)) {
            item {
                DayHeading(
                    day = day.logical.day,
                    started = day.startedCount,
                    tracked = day.trackedTotal,
                    unrecorded = day.unrecordedCount,
                    onOpenWeek = onOpenWeek,
                )
            }

            item { DayBar(day.logical.day) }

            // Earlier slots collapse to one line. They are the part of the day
            // that is finished, and a day view that opens on breakfast makes you
            // scroll past it every time.
            if (day.past.isNotEmpty()) {
                item {
                    EarlierStrip(
                        count = day.past.size,
                        started = day.past.count { it.started },
                        unrecorded = day.past.count { it.unrecorded },
                        expanded = pastExpanded,
                        onToggle = { pastExpanded = !pastExpanded },
                    )
                }
                if (pastExpanded) {
                    items(day.past, key = { it.slot.id }) { row ->
                        SlotRowItem(row, onStart = { viewModel.start(row) })
                    }
                }
            }

            if (day.isToday) {
                item { NowLine(state.now) }
            }

            day.current?.let { row ->
                item(key = row.slot.id) {
                    SlotRowItem(row, onStart = { viewModel.start(row) }, prominent = true)
                }
            }

            items(day.upcoming, key = { it.slot.id }) { row ->
                SlotRowItem(row, onStart = { viewModel.start(row) })
            }

            // "Nothing running" is a fact about the present moment, not about
            // whatever day is being looked at. Trailing it under Friday while
            // browsing on a Tuesday reads as a statement about Friday.
            val next = state.nextDayStartsAt
            if (state.resting && next != null && next.dayOfWeek == state.selected) {
                item { RestingNote(next) }
            }
        }
    }
}

@Composable
private fun DayPager(
    selected: DayOfWeek,
    today: DayOfWeek?,
    onSelect: (DayOfWeek) -> Unit,
) {
    val colors = JarvisTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.x3, vertical = Space.x3),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (weekday in DayOfWeek.entries) {
            val chosen = weekday == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(Corner.Sm)
                    .background(if (chosen) colors.brandCore else colors.surface, Corner.Sm)
                    .clickable { onSelect(weekday) }
                    .padding(vertical = Space.x2),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = weekday.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                        style = JarvisTheme.typography.labelMedium,
                        color = if (chosen) colors.onBrand else colors.inkMuted,
                    )
                    // A dot rather than a second colour: the pager already uses
                    // fill for selection, and today is a different fact from
                    // the one being looked at.
                    if (weekday == today) {
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .size(3.dp)
                                .background(if (chosen) colors.onBrand else colors.brandCore, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeading(
    day: RoutineDay,
    started: Int,
    tracked: Int,
    unrecorded: Int,
    onOpenWeek: () -> Unit,
) {
    val colors = JarvisTheme.colors
    Column(
        Modifier.padding(
            start = Space.Gutter,
            end = Space.Gutter,
            top = Space.x3,
            bottom = Space.x2,
        ),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = day.weekday.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                style = JarvisTheme.typography.titleLarge,
                color = colors.ink,
            )
            day.note?.let {
                Spacer(Modifier.width(Space.x2))
                Text(
                    text = it,
                    style = JarvisTheme.typography.bodySmall,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = shortTime(day.startsAt.toString()) + " – " + shortTime(day.endsAt.toString()),
                style = JarvisTheme.typography.bodySmall.tabularNums(),
                color = colors.inkMuted,
                modifier = Modifier.padding(end = Space.x2),
            )
            CircleIconButton(onClick = onOpenWeek, diameter = 32.dp) {
                Icon(
                    Icons.Rounded.BarChart,
                    contentDescription = "Where the hours go",
                    tint = colors.inkMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Spacer(Modifier.height(Space.x2))
        Text(
            text = buildString {
                append(started)
                append(" of ")
                append(tracked)
                append(" started")
                if (unrecorded > 0) {
                    append(" · ")
                    append(unrecorded)
                    append(" not recorded")
                }
            },
            style = JarvisTheme.typography.bodySmall,
            color = colors.inkMuted,
        )
    }
}

/**
 * The stacked bar from Joy's own file, kept because it is the best thing in it:
 * the shape of a day is legible before a single label is read.
 */
@Composable
private fun DayBar(day: RoutineDay) {
    val total = day.slots.sumOf { it.durationMinutes }.toFloat().coerceAtLeast(1f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x2)
            .height(9.dp)
            .clip(Corner.Pill),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        for (slot in day.slots) {
            Box(
                Modifier
                    // fillMaxHeight, not fillMaxWidth: weight already decides the
                    // width, and a Box with no content and no height constraint
                    // wraps to nothing — so the bar reserved its space and then
                    // painted a nine-pixel strip of nothing.
                    .weight(slot.durationMinutes / total)
                    .fillMaxHeight()
                    .background(routineCategoryColor(slot.categoryId)),
            )
        }
    }
}

@Composable
private fun EarlierStrip(
    count: Int,
    started: Int,
    unrecorded: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = JarvisTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = Space.Gutter, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildString {
                append(count)
                append(" earlier · ")
                append(started)
                append(" started")
                if (unrecorded > 0) {
                    append(", ")
                    append(unrecorded)
                    append(" not recorded")
                }
            },
            style = JarvisTheme.typography.bodySmall,
            color = colors.inkMuted,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "Hide earlier" else "Show earlier",
            tint = colors.inkMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun NowLine(now: LocalDateTime) {
    val colors = JarvisTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.Gutter, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "NOW · " + now.format(TIME),
            style = JarvisTheme.typography.labelSmall.tabularNums(),
            color = colors.brandCore,
        )
        Spacer(Modifier.width(Space.x2))
        Box(Modifier.weight(1f).height(1.dp).background(colors.brandCore.copy(alpha = 0.35f)))
    }
}

@Composable
private fun SlotRowItem(
    row: SlotRow,
    onStart: () -> Unit,
    prominent: Boolean = false,
) {
    val colors = JarvisTheme.colors
    val tracked = row.slot.kind == SlotKind.Tracked

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter)
            .padding(vertical = if (prominent) Space.x4 else Space.x3)
            // Scaffold is drawn so the day reads as continuous, but it is not
            // asking for anything and should not compete with what is.
            .alpha(if (tracked) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(if (prominent) 38.dp else 26.dp)
                .clip(Corner.Pill)
                .background(routineCategoryColor(row.slot.categoryId)),
        )
        Spacer(Modifier.width(Space.x3))

        Column(Modifier.weight(1f)) {
            Text(
                text = row.slot.label,
                style = if (prominent) JarvisTheme.typography.titleMedium
                else JarvisTheme.typography.bodyLarge,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle(row),
                style = JarvisTheme.typography.bodySmall.tabularNums(),
                color = if (row.unrecorded) colors.status.incomplete else colors.inkMuted,
            )
        }

        // Only tracked slots carry a target. Eighty checkboxes a week is the
        // thing this design exists to avoid (§4.3).
        if (tracked) {
            Spacer(Modifier.width(Space.x3))
            StartTarget(started = row.started, prominent = prominent, onClick = onStart)
        }
    }
}

@Composable
private fun StartTarget(started: Boolean, prominent: Boolean, onClick: () -> Unit) {
    val colors = JarvisTheme.colors
    val size = if (prominent) 30.dp else 26.dp
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (started) Modifier.background(colors.brandCore, CircleShape)
                else Modifier.border(1.dp, colors.hairline, CircleShape)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (started) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Started — tap to undo",
                tint = colors.onBrand,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun RestingNote(startsAt: LocalDateTime) {
    val colors = JarvisTheme.colors
    Column(Modifier.fillMaxWidth().padding(Space.Gutter)) {
        Text(
            text = "Nothing running.",
            style = JarvisTheme.typography.bodyMedium,
            color = colors.inkMuted,
        )
        Text(
            text = "Next day starts " + startsAt.format(TIME) + ".",
            style = JarvisTheme.typography.bodySmall.tabularNums(),
            color = colors.inkMuted,
        )
    }
}

/**
 * Planned time, and what actually happened where they differ.
 *
 * The planned range always shows: the point of the view is adherence, and a row
 * that hid the plan once you started would remove the only thing to compare
 * against.
 */
private fun subtitle(row: SlotRow): String {
    val planned = row.plannedStart.format(TIME) + " – " + row.plannedEnd.format(TIME)
    return when {
        // Only the two real buffers say this now. It was on every free slot
        // once, which made it mean nothing — the copy was right and the
        // classification was wrong.
        row.slot.kind == SlotKind.Buffer -> planned + " · keep clear"

        // `drifted` is null when no tolerance has reached the app. Null is not
        // "on time" — there is nothing to be on time against — so an unknown
        // falls through to the plain "started" line and claims neither way.
        row.actualStart != null && row.drifted == true ->
            planned + " · started " + row.actualStart.format(TIME)
        row.actualStart != null -> planned + " · started"
        row.unrecorded -> planned + " · not recorded"
        else -> planned
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm")

/** Formats a `LocalTime.toString()` such as "08:45" or "01:00" for display. */
private fun shortTime(iso: String): String {
    val hour = iso.substring(0, 2).toInt()
    val minute = iso.substring(3, 5)
    val display = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return display.toString() + ":" + minute
}
