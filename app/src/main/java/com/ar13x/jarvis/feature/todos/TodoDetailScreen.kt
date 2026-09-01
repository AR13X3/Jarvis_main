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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.model.Todo
import com.ar13x.jarvis.core.model.TodoStatus
import com.ar13x.jarvis.core.ui.Deadline
import com.ar13x.jarvis.core.ui.DueDateFormat
import com.ar13x.jarvis.core.ui.LoadState
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.DeadlinePickerDialog
import com.ar13x.jarvis.designsystem.component.JarvisCard
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
 * The screen exists mostly to make three things visible that a list row cannot:
 * the description, **what is chasing it**, and the fact that a deadline can be
 * taken away again.
 *
 * That last one is not decoration. Clearing a deadline is how a dated to-do
 * returns to the backlog (§5.2), and it is the single edit the app was unable
 * to send until `todoPatchBody` existed — `JarvisJson` omits Kotlin nulls, so a
 * nullable field would have asked the server to change nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDetailScreen(
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
    viewModel: TodoDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = JarvisTheme.colors
    val sheet = rememberModalBottomSheetState()

    state.candidates?.let { candidates ->
        ModalBottomSheet(
            onDismissRequest = viewModel::closePicker,
            sheetState = sheet,
            containerColor = colors.surface,
        ) {
            LinkPicker(candidates, onPick = viewModel::link)
        }
    }

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

            is LoadState.Ready -> Content(loaded.data, state, viewModel, onOpenTask)
        }
    }
}

@Composable
private fun Content(
    todo: Todo,
    state: TodoDetailUiState,
    viewModel: TodoDetailViewModel,
    onOpenTask: (Long) -> Unit,
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

        // --- reminders -----------------------------------------------------------
        item { SectionHeader("Reminders") }
        item {
            Text(
                // The §5.1 sentence, said where it matters. A to-do with no
                // reminder is not half-configured; it is a note that nothing is
                // chasing, which is most of them.
                text = if (todo.hasReminders) {
                    "These fire, chase and extend. The to-do itself never interrupts you."
                } else {
                    "Nothing is chasing this. A to-do does not fire on its own — " +
                        "a reminder is a separate thing you point at it."
                },
                style = JarvisTheme.typography.bodySmall,
                color = colors.inkMuted,
                modifier = Modifier.padding(horizontal = Space.Gutter),
            )
        }

        if (state.remindersLoading) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(Space.x6),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = colors.brandCore) }
            }
        }

        items(state.reminders, key = { it.id }) { task ->
            ReminderRow(
                task = task,
                onOpen = { onOpenTask(task.id) },
                onUnlink = { viewModel.unlink(task.id) },
            )
        }

        item {
            TextButton(
                onClick = viewModel::openPicker,
                modifier = Modifier.padding(horizontal = Space.x3),
            ) {
                // "Point an existing reminder at this" rather than "Add a
                // reminder", because it does not create one. §5.1: setting a
                // reminder means creating the TASK through the flow that already
                // fires and chases, then pointing at it. A button that read
                // "Add" would promise the creating half.
                Text("Point a reminder at this", style = JarvisTheme.typography.labelLarge)
            }
        }

        // An id that could not be fetched is absent rather than fatal — but the
        // count disagreeing with the rows would look like a bug, so it says so.
        val missing = todo.taskIds.size - state.reminders.size
        if (!state.remindersLoading && missing > 0) {
            item {
                Text(
                    text = missing.toString() + " reminder" + (if (missing == 1) "" else "s") +
                        " could not be loaded.",
                    style = JarvisTheme.typography.bodySmall,
                    color = colors.status.incomplete,
                    modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.x2),
                )
            }
        }

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
private fun ReminderRow(task: Task, onOpen: () -> Unit, onUnlink: () -> Unit) {
    val colors = JarvisTheme.colors
    JarvisCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x1),
        onClick = onOpen,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.title, style = JarvisTheme.typography.titleMedium, color = colors.ink)
                Spacer(Modifier.height(Space.x1))
                Text(
                    text = task.recurrenceText ?: task.dueDate.format(DAY),
                    style = JarvisTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            CircleIconButton(onClick = onUnlink, diameter = 32.dp) {
                Icon(
                    Icons.Rounded.Close,
                    // Says what it does. "Remove" would read as deleting the
                    // reminder, and the task survives and keeps firing.
                    contentDescription = "Detach from this to-do — the reminder keeps firing",
                    tint = colors.inkMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
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

/**
 * The reminders that could be pointed at this to-do.
 *
 * Live tasks only — `active` and `awaiting` — minus the ones already here. A
 * task that belongs to a *different* to-do is still listed: the app cannot know
 * which without fetching every to-do, and the gateway answers `409` naming the
 * owner. An honest error beats a row hidden for a reason nobody can see.
 */
@Composable
private fun LinkPicker(candidates: LoadState<List<Task>>, onPick: (Long) -> Unit) {
    val colors = JarvisTheme.colors

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Space.Gutter, vertical = Space.x2),
    ) {
        Text(
            "Point a reminder at this",
            style = JarvisTheme.typography.titleLarge,
            color = colors.ink,
        )
        Spacer(Modifier.height(Space.x1))
        Text(
            "The reminder keeps its own schedule. Linking says what it is about.",
            style = JarvisTheme.typography.bodySmall,
            color = colors.inkMuted,
        )
        Spacer(Modifier.height(Space.x3))

        when (candidates) {
            is LoadState.Loading -> Box(
                Modifier.fillMaxWidth().padding(Space.x6),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = colors.brandCore) }

            is LoadState.Failed -> Text(
                candidates.reason.message(),
                style = JarvisTheme.typography.bodyMedium,
                color = colors.inkMuted,
            )

            is LoadState.Ready -> if (candidates.data.isEmpty()) {
                Text(
                    // Names the filter rather than saying "nothing found",
                    // which would read as "you have no reminders" when the real
                    // answer is usually "they are all already linked here".
                    "No live reminders left to link. Only active and awaiting " +
                        "ones can be pointed at a to-do.",
                    style = JarvisTheme.typography.bodyMedium,
                    color = colors.inkMuted,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(candidates.data, key = { it.id }) { task ->
                        JarvisCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.x1),
                            onClick = { onPick(task.id) },
                        ) {
                            Text(
                                task.title,
                                style = JarvisTheme.typography.titleMedium,
                                color = colors.ink,
                            )
                            Spacer(Modifier.height(Space.x1))
                            Text(
                                task.recurrenceText ?: task.dueDate.format(DAY),
                                style = JarvisTheme.typography.bodySmall,
                                color = colors.inkMuted,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.x4))
    }
}
