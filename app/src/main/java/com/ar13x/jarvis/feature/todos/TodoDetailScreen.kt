package com.ar13x.jarvis.feature.todos

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
import androidx.compose.material.icons.rounded.Close
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
import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.model.Todo
import com.ar13x.jarvis.core.model.TodoStatus
import com.ar13x.jarvis.core.ui.LoadState
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.component.JarvisChip
import com.ar13x.jarvis.designsystem.component.SectionHeader
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
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
@Composable
fun TodoDetailScreen(
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
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
            Column(Modifier.padding(horizontal = Space.Gutter)) {
                Text(
                    // `due_date` is the server's local day, never derived here.
                    text = todo.dueDate?.format(DAY) ?: "No deadline — this is in the backlog.",
                    style = JarvisTheme.typography.bodyMedium,
                    color = if (todo.dueDate == null) colors.inkMuted else colors.ink,
                )
                if (todo.dueAt != null) {
                    Spacer(Modifier.height(Space.x1))
                    TextButton(
                        onClick = { viewModel.setDueAt(null) },
                        contentPadding = PaddingValues(0.dp),
                    ) {
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
