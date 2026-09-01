package com.ar13x.jarvis.feature.todos

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ar13x.jarvis.core.data.message
import com.ar13x.jarvis.core.model.Todo
import com.ar13x.jarvis.core.model.TodoStatus
import com.ar13x.jarvis.core.ui.LoadState
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.component.JarvisChip
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import java.time.format.DateTimeFormatter

/**
 * To-dos (v2 plan §5).
 *
 * **A to-do is not a reminder, and this screen exists so they stop being
 * confused.** A reminder fires, chases and extends; a to-do just sits there
 * being true. §5.1 makes a to-do the *owner* of reminders rather than a kind of
 * one, so a row here shows whether anything is chasing it — and if nothing is,
 * that is a legitimate state rather than a mistake.
 *
 * The **backlog is a filter**, not a second screen (§5.2). It is a chip in the
 * same row as the status filters, over the same ordering, so the two cannot
 * drift apart.
 */
@Composable
fun TodoListScreen(
    onBack: () -> Unit,
    onOpenTodo: (Long) -> Unit,
    viewModel: TodoListViewModel = hiltViewModel(),
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
                    TextButton(onClick = viewModel::load) { Text("Try again") }
                }
            }

            is LoadState.Ready -> if (content.data.isEmpty()) {
                Centred {
                    Text(
                        // Says which emptiness it is. "Nothing here" over an
                        // active filter is the same sentence as "you have no
                        // to-dos", and they are very different facts.
                        text = if (state.filters.isNarrowed) {
                            "Nothing matches these filters."
                        } else {
                            "No to-dos yet."
                        },
                        style = JarvisTheme.typography.bodyMedium,
                        color = colors.inkMuted,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = Space.x12)) {
                    items(content.data, key = { it.todoId }) { todo ->
                        TodoRow(
                            todo = todo,
                            onOpen = { onOpenTodo(todo.todoId) },
                            onAdvance = { viewModel.advance(todo) },
                        )
                    }
                }
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
        Text("To-dos", style = JarvisTheme.typography.titleLarge, color = colors.ink)
    }
}

@Composable
private fun Filters(state: TodoListUiState, viewModel: TodoListViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.Gutter, vertical = Space.x2),
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        // The backlog leads, because it is the one filter that answers a
        // question ("what have I not scheduled") rather than narrowing a list.
        JarvisChip(
            label = "Backlog",
            selected = state.filters.undatedOnly,
            onClick = viewModel::toggleBacklog,
        )
        // `cancelled` is absent on purpose: a cancelled to-do is not something
        // you browse, and offering it here would put a fourth chip in front of
        // Joy for the state she looks at least.
        for (status in listOf(TodoStatus.Open, TodoStatus.Doing, TodoStatus.Done)) {
            JarvisChip(
                label = status.label,
                selected = status in state.filters.statuses,
                onClick = { viewModel.toggleStatus(status) },
            )
        }
        for (tag in state.tags) {
            JarvisChip(
                label = tag,
                selected = state.filters.tag == tag,
                onClick = { viewModel.toggleTag(tag) },
            )
        }
    }
}

@Composable
private fun TodoRow(todo: Todo, onOpen: () -> Unit, onAdvance: () -> Unit) {
    val colors = JarvisTheme.colors

    JarvisCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x1)
            // Resolved rows drop back without disappearing — the same treatment
            // the task list gives a completed task.
            .alpha(if (todo.isResolved) 0.55f else 1f),
        onClick = onOpen,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(todo.status, onAdvance)
            Spacer(Modifier.width(Space.x3))
            Column(Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    style = JarvisTheme.typography.titleMedium,
                    color = if (todo.isResolved) colors.inkMuted else colors.ink,
                    // Struck through for cancelled and only for cancelled — a
                    // decision, not a lapse. Same rule as `StatusStyle`.
                    textDecoration = if (todo.status == TodoStatus.Cancelled) {
                        TextDecoration.LineThrough
                    } else {
                        null
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Subtitle(todo)
            }
        }
    }
}

@Composable
private fun Subtitle(todo: Todo) {
    val colors = JarvisTheme.colors
    val parts = buildList {
        // `due_date` is the server's local day. Never derived from `due_at`
        // here — that is §3.2's highest-risk defect.
        todo.dueDate?.let { add("due " + it.format(DAY)) }
        // Says nothing when there are none, rather than "0 reminders". A to-do
        // with nothing chasing it is ordinary (§5.1), not incomplete.
        if (todo.hasReminders) {
            add(
                todo.taskIds.size.toString() + " reminder" +
                    if (todo.taskIds.size == 1) "" else "s",
            )
        }
        addAll(todo.tags)
    }
    if (parts.isEmpty()) return

    Spacer(Modifier.height(Space.x1))
    Text(
        text = parts.joinToString(" · "),
        style = JarvisTheme.typography.bodySmall,
        color = colors.inkMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The tap target that advances the status.
 *
 * Direct, with no confirmation, and the justification is §5.4's: a tap cannot
 * misparse, and tapping again moves it on and eventually back to where it
 * started. It is separate from the row's own click so that opening a to-do and
 * advancing it are never the same gesture.
 */
@Composable
private fun StatusDot(status: TodoStatus, onAdvance: () -> Unit) {
    val colors = JarvisTheme.colors
    val filled = status == TodoStatus.Done
    val accent = when (status) {
        TodoStatus.Open -> colors.inkMuted
        TodoStatus.Doing -> colors.brandCore
        TodoStatus.Done -> colors.status.completed
        TodoStatus.Cancelled -> colors.status.cancelled
    }

    CircleIconButton(
        onClick = onAdvance,
        diameter = 28.dp,
        background = if (filled) accent else colors.surfaceSunk,
    ) {
        when (status) {
            TodoStatus.Done -> Icon(
                Icons.Rounded.Check,
                contentDescription = "Done — tap to reopen",
                tint = colors.onBrand,
                modifier = Modifier.size(15.dp),
            )
            // `doing` is a half-filled ring rather than a second check: it is a
            // stage, and a check would read as finished at a glance.
            TodoStatus.Doing -> Box(
                Modifier
                    .size(11.dp)
                    .clip(Corner.Pill)
                    .background(accent),
            )
            else -> Box(
                Modifier
                    .size(11.dp)
                    .clip(Corner.Pill)
                    .background(accent.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** True when the empty state has to say "nothing matches" rather than "nothing". */
private val TodoFilters.isNarrowed: Boolean
    get() = undatedOnly || tag != null ||
        statuses != setOf(TodoStatus.Open, TodoStatus.Doing)

private val TodoStatus.label: String
    get() = when (this) {
        TodoStatus.Open -> "Open"
        TodoStatus.Doing -> "Doing"
        TodoStatus.Done -> "Done"
        TodoStatus.Cancelled -> "Cancelled"
    }

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
