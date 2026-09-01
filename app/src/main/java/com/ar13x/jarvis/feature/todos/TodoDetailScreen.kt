package com.ar13x.jarvis.feature.todos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ar13x.jarvis.core.data.message
import com.ar13x.jarvis.core.model.Todo
import com.ar13x.jarvis.core.model.TodoStatus
import com.ar13x.jarvis.core.ui.Deadline
import com.ar13x.jarvis.core.ui.DueDateFormat
import com.ar13x.jarvis.core.ui.LoadState
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.DeadlinePickerDialog
import com.ar13x.jarvis.designsystem.component.JarvisChip
import com.ar13x.jarvis.designsystem.component.SectionHeader
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One to-do (v2 plan §5).
 *
 * The screen exists to show three things a list row cannot: the description, the
 * full status set including `cancelled`, and **the deadline — which can now be
 * set, changed and taken away.**
 *
 * Setting it is new (§9.1), and its absence was what made the rest of this
 * screen misleading: the only deadline control used to be "Clear deadline", so
 * the app could undo something it had no way to do. Clearing is not decoration
 * either — it is how a dated to-do returns to the backlog (§5.2), and it is the
 * edit the app could not send at all until `todoPatchBody` existed, because
 * `JarvisJson` omits Kotlin nulls and a nullable field would have asked the
 * server to change nothing.
 *
 * What is deliberately **not** here any more is the reminders section (§9.5).
 */
@Composable
fun TodoDetailScreen(
    onBack: () -> Unit,
    viewModel: TodoDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = JarvisTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        Header(onBack)

        state.transientFailure?.let { reason ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.Gutter),
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

        when (val loaded = state.todo) {
            is LoadState.Loading -> Centred { CircularProgressIndicator(color = colors.brandCore) }

            is LoadState.Failed -> Centred {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        loaded.reason.message(),
                        style = JarvisTheme.typography.bodyMedium,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Space.x8),
                    )
                    TextButton(onClick = viewModel::load) { Text("Try again") }
                }
            }

            is LoadState.Ready -> Content(loaded.data, state, viewModel)
        }
    }
}

@Composable
private fun Content(
    todo: Todo,
    state: TodoDetailUiState,
    viewModel: TodoDetailViewModel,
) {
    val colors = JarvisTheme.colors

    LazyColumn(contentPadding = PaddingValues(bottom = Space.x12)) {
        item {
            Column(Modifier.padding(horizontal = Space.Gutter)) {
                Text(todo.title, style = JarvisTheme.typography.headlineSmall, color = colors.ink)
                if (todo.description.isNotBlank()) {
                    Spacer(Modifier.height(Space.x2))
                    Text(
                        todo.description,
                        style = JarvisTheme.typography.bodyMedium,
                        color = colors.inkMuted,
                    )
                }
            }
        }

        // --- status ------------------------------------------------------------
        item { SectionHeader("Status") }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.Gutter),
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                // All four here, unlike the list's three. `cancelled` is a
                // decision rather than a step, so it belongs on the screen where
                // a decision can be deliberate — not under a tap target that
                // also does open → doing → done.
                for (status in TodoStatus.entries) {
                    JarvisChip(
                        label = status.detailLabel,
                        selected = todo.status == status,
                        onClick = { viewModel.setStatus(status) },
                    )
                }
            }
        }

        // --- deadline -----------------------------------------------------------
        item { SectionHeader("Deadline") }
        item {
            DeadlineSection(
                todo = todo,
                mismatch = state.deadlineDayMismatch,
                onSet = viewModel::setDeadline,
                onClear = viewModel::clearDeadline,
            )
        }

        // There is no "Reminders" section any more, and its absence is the
        // point (§9.5). A to-do used to list the tasks chasing it and offer
        // "Point a reminder at this"; Joy had that removed because the deadline
        // above and a linked reminder both answered "when is this due", and two
        // answers is a consistency problem forever. A thing that should chase
        // you is a reminder, and it is made in the Tasks tab.

        if (todo.tags.isNotEmpty()) {
            item { SectionHeader("Tags") }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.Gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    // Not tappable here. A tag is the summary subscription's
                    // taxonomy (§7) and editing one from a detail screen would
                    // quietly change what a weekly summary covers.
                    todo.tags.forEach { JarvisChip(label = it, selected = false, onClick = {}) }
                }
            }
        }
    }
}

/**
 * The deadline, and the two things you can do to it.
 *
 * **Until this existed a to-do could only ever have a deadline taken away.**
 * `Todo` has carried `due_at` since migration 0009 and the only control on this
 * screen was "Clear deadline", so the sole way to acquire one was to ask the
 * agent in chat — which meant the app could undo something it could not do. It
 * was not a missing feature so much as half a feature that read as complete.
 *
 * The row is the affordance: tapping it opens the picker whether or not there is
 * a deadline already, so setting and changing are the same gesture. "Clear" stays
 * a separate, quieter control underneath, because it is the destructive one and
 * because the sentence explaining what it does is worth the room.
 */
@Composable
private fun DeadlineSection(
    todo: Todo,
    mismatch: DeadlineDayMismatch?,
    onSet: (Instant, LocalDate) -> Unit,
    onClear: () -> Unit,
) {
    val colors = JarvisTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    var picking by remember { mutableStateOf(false) }

    // The day is the server's and the time is read off the instant — and the
    // time is shown only when the deadline actually names one (§9.1: most
    // to-dos want a day, not an hour).
    val label = DueDateFormat.forTodo(todo.dueDate, todo.dueAt, zone)

    if (picking) {
        DeadlinePickerDialog(
            onDismiss = { picking = false },
            onPick = { dueAt, day ->
                picking = false
                onSet(dueAt, day)
            },
            // Seeded from the server's day, never from a day this screen
            // derived. `initialTime` is a time and may be read off the instant.
            initialDay = todo.dueDate,
            initialTime = todo.dueAt?.let { Deadline.timeOf(it, zone) },
            zone = zone,
        )
    }

    Column(Modifier.padding(horizontal = Space.Gutter)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Corner.Sm)
                .clickable(
                    onClickLabel = if (todo.dueAt == null) "Set a deadline" else "Change the deadline",
                    onClick = { picking = true },
                )
                .padding(vertical = Space.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            Icon(
                Icons.Rounded.EditCalendar,
                contentDescription = null,
                tint = if (label == null) colors.inkMuted else colors.brandCore,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = label ?: "Set a deadline",
                    style = JarvisTheme.typography.bodyLarge,
                    color = if (label == null) colors.inkMuted else colors.ink,
                )
                if (label == null) {
                    Text(
                        // §5.2: undated is the ordinary case, not an omission.
                        // The line says where the to-do currently is rather
                        // than nagging about a field being empty.
                        "No deadline — this is in the backlog.",
                        style = JarvisTheme.typography.bodySmall,
                        color = colors.inkMuted,
                    )
                }
            }
        }

        // Almost never drawn. It means the gateway filed the deadline under a
        // different calendar day from the one that was tapped, which can only
        // happen if its zone and the phone's have parted company — and which
        // would otherwise look like the app losing a day at random.
        if (mismatch != null) {
            Text(
                text = "Saved, but the server filed this under " +
                    mismatch.stored.format(DAY) + " rather than " +
                    mismatch.asked.format(DAY) + ". The time is right; the day is not.",
                style = JarvisTheme.typography.bodySmall,
                color = colors.status.incomplete,
                modifier = Modifier.padding(vertical = Space.x1),
            )
        }

        if (todo.dueAt != null) {
            Spacer(Modifier.height(Space.x1))
            TextButton(onClick = onClear, contentPadding = PaddingValues(0.dp)) {
                Text("Clear deadline", style = JarvisTheme.typography.labelLarge)
            }
            Text(
                "Moves it back to the backlog. The reminders below keep firing.",
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
        Text("To-do", style = JarvisTheme.typography.titleLarge, color = colors.ink)
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private val TodoStatus.detailLabel: String
    get() = when (this) {
        TodoStatus.Open -> "Open"
        TodoStatus.Doing -> "Doing"
        TodoStatus.Done -> "Done"
        TodoStatus.Cancelled -> "Cancelled"
    }

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
