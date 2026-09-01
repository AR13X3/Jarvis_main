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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
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
import com.ar13x.jarvis.core.ui.DueDateFormat
import com.ar13x.jarvis.core.ui.LoadState
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.component.JarvisChip
import com.ar13x.jarvis.designsystem.component.PriorityGlyph
import com.ar13x.jarvis.designsystem.component.SectionHeader
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space

/**
 * To-dos (v2 plan §5).
 *
 * **A to-do is not a reminder, and this screen exists so they stop being
 * confused.** A reminder fires, chases and extends; a to-do just sits there
 * being true until you do it. They are now adjacent tabs (§9.2) rather than one
 * being tucked behind an icon in the other, which is the arrangement that makes
 * the distinction visible instead of merely documented.
 *
 * A row no longer shows what is chasing a to-do, because nothing does — the
 * to-do → reminder link is deleted (§9.5) and a to-do's own deadline is the
 * deadline.
 *
 * The **backlog is a filter**, not a second screen (§5.2). It is a chip in the
 * same row as the status filters, over the same ordering, so the two cannot
 * drift apart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    onOpenTodo: (Long) -> Unit,
    viewModel: TodoListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = JarvisTheme.colors
    var composing by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState()

    if (composing) {
        ModalBottomSheet(
            onDismissRequest = { composing = false },
            sheetState = sheet,
            containerColor = colors.surface,
        ) {
            NewTodoSheet(
                onCreate = { title, description, tags, dueAt ->
                    composing = false
                    viewModel.create(title, description, tags, dueAt)
                },
                onCancel = { composing = false },
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        Header(onNew = { composing = true })
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
                // Grouped by status, with a count per group (§9.4.4). It reads
                // like a board without being one — Joy ruled out columns and
                // dragging in v2 §5, and grouping buys most of the legibility a
                // board would for none of the horizontal space this screen does
                // not have.
                //
                // In lifecycle order rather than by size, so a group does not
                // move when a row is ticked. Within a group the gateway's own
                // ordering is untouched.
                val grouped = TodoStatus.entries.mapNotNull { status ->
                    content.data
                        .filter { it.status == status }
                        .takeIf { it.isNotEmpty() }
                        ?.let { status to it }
                }

                LazyColumn(contentPadding = PaddingValues(bottom = Space.x12)) {
                    grouped.forEach { (status, rows) ->
                        // Shown even when there is only one group. The count is
                        // the point, and a header that appears and vanishes as
                        // filters change is harder to read than one that stays.
                        item(key = "group-" + status.name) {
                            SectionHeader(status.label + " · " + rows.size)
                        }
                        items(rows, key = { it.todoId }) { todo ->
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
}

@Composable
private fun Header(onNew: () -> Unit) {
    val colors = JarvisTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.Gutter, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No back arrow: this is a tab root now (§9.2), and there is nothing
        // above it. A back arrow that leaves the tab is worse than none.
        Text("To-dos", style = JarvisTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.weight(1f))
        // The one saturated element on this screen, same as the + on the task
        // list. Capture is the thing this screen is for -- most of these are
        // notes with nowhere to go yet -- so it should be the easiest thing
        // to hit.
        CircleIconButton(onClick = onNew, diameter = 40.dp, background = colors.brandCore) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = "New to-do",
                tint = colors.onBrand,
                modifier = Modifier.size(20.dp),
            )
        }
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
            // After the title rather than before it, so a column of glyphs
            // never pushes the titles out of alignment — most rows are `normal`
            // and draw nothing here at all.
            PriorityGlyph(todo.priority, modifier = Modifier.padding(start = Space.x2))
        }
    }
}

@Composable
private fun Subtitle(todo: Todo) {
    val colors = JarvisTheme.colors
    val parts = buildList {
        // The day is the server's and the hour is only shown when the deadline
        // actually names one — §3.2 for the first half, §9.1 for the second.
        DueDateFormat.forTodo(todo.dueDate, todo.dueAt)?.let { add("due " + it) }
        // The reminder count that used to sit here is gone with the link (§9.5).
        // Sub-task progress, from the gateway's own rollup (§9.4.2). Said only
        // when there are children: "0 of 0" on every ordinary to-do would be a
        // column of noise claiming something is unfinished when nothing is.
        if (todo.hasChildren) add(todo.childDone.toString() + " of " + todo.childCount)
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

