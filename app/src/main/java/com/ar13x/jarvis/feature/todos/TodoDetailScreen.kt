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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ar13x.jarvis.core.data.message
import com.ar13x.jarvis.core.model.Todo
import com.ar13x.jarvis.core.model.TodoEvent
import com.ar13x.jarvis.core.model.TodoPriority
import com.ar13x.jarvis.core.model.TodoStatus
import com.ar13x.jarvis.core.model.TodoVerb
import com.ar13x.jarvis.core.ui.Deadline
import com.ar13x.jarvis.core.ui.DueDateFormat
import com.ar13x.jarvis.core.ui.LoadState
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.DeadlinePickerDialog
import com.ar13x.jarvis.designsystem.component.JarvisChip
import com.ar13x.jarvis.designsystem.component.icon
import com.ar13x.jarvis.designsystem.component.shortLabel
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

        // --- priority ------------------------------------------------------------
        item { SectionHeader("Priority") }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.Gutter),
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                // All four, including `normal`. Priority has no "unset" state
                // (§9.4.1) so there is always exactly one selected chip, and
                // returning something to ordinary has to be as easy as raising it.
                for (priority in TodoPriority.entries) {
                    JarvisChip(
                        label = priority.shortLabel,
                        selected = todo.priority == priority,
                        onClick = { viewModel.setPriority(priority) },
                        icon = priority.icon,
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

        // --- sub-tasks -----------------------------------------------------------
        //
        // Only on a top-level to-do. One level is enforced by the gateway with a
        // composite foreign key (§9.4.2), so offering "add a sub-task" on a
        // sub-task would be offering a 422.
        if (!todo.isSubTask) {
            item {
                SectionHeader(
                    if (todo.hasChildren) {
                        "Sub-tasks · " + todo.childDone + " of " + todo.childCount
                    } else {
                        "Sub-tasks"
                    },
                )
            }
            items(state.children, key = { "child-" + it.todoId }) { child ->
                ChildRow(child = child, onAdvance = { viewModel.advanceChild(child) })
            }

            // THE HEADING AND THE ROWS COME FROM TWO DIFFERENT FETCHES. The
            // count above is the gateway's rollup, carried on the to-do itself;
            // the rows are a second call, and `loadChildren` swallows its
            // failure so that a broken sub-task read cannot take the whole
            // screen down. Without this line the two disagree silently and
            // "3 of 5" sits above nothing at all, which reads as the sub-tasks
            // having been deleted.
            if (todo.hasChildren && state.children.isEmpty() && !state.childrenLoading) {
                item {
                    Text(
                        text = todo.childCount.toString() + " sub-task" +
                            (if (todo.childCount == 1) "" else "s") +
                            " could not be loaded.",
                        style = JarvisTheme.typography.bodySmall,
                        color = colors.status.incomplete,
                        modifier = Modifier.padding(
                            horizontal = Space.Gutter,
                            vertical = Space.x1,
                        ),
                    )
                }
            }

            item { AddChild(onAdd = viewModel::addChild) }
        }

        // --- activity -------------------------------------------------------------
        item { SectionHeader("Activity") }
        item { Trail(state.history) }

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
    // `rememberSaveable`, not `remember`. This composable lives inside a
    // `LazyColumn` item, and a lazy item's plain `remember` is discarded the
    // moment the item scrolls out of view. `LazyColumn` wraps each item in a
    // `SaveableStateProvider`, so the saveable version survives that — and a
    // rotation, which plain `remember` also loses.
    var picking by rememberSaveable { mutableStateOf(false) }

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
/**
 * One sub-task, on its parent's screen.
 *
 * Deliberately thinner than a `TodoRow`: no priority glyph, no tags, no
 * deadline. A sub-task that renders as richly as a top-level to-do invites you
 * to manage it here, and a parent screen is not a second list — tapping the dot
 * advances it, and anything wanting more than that is a to-do in its own right.
 */
@Composable
private fun ChildRow(child: Todo, onAdvance: () -> Unit) {
    val colors = JarvisTheme.colors

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x1)
            .alpha(if (child.isResolved) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        CircleIconButton(
            onClick = onAdvance,
            diameter = 28.dp,
            background = if (child.status == TodoStatus.Done) colors.brandTint else colors.surfaceSunk,
        ) {
            if (child.status == TodoStatus.Done) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Reopen this sub-task",
                    tint = colors.brandCore,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Icon(
                    Icons.Rounded.SubdirectoryArrowRight,
                    contentDescription = "Advance this sub-task",
                    tint = colors.inkMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = child.title,
            style = JarvisTheme.typography.bodyMedium,
            color = if (child.isResolved) colors.inkMuted else colors.ink,
            textDecoration = if (child.status == TodoStatus.Cancelled) {
                TextDecoration.LineThrough
            } else {
                null
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Adding a sub-task: one field, and it stays on the screen.
 *
 * No sheet and no navigation, unlike capturing a top-level to-do. A sub-task is
 * almost always one of several typed in a row — "the parts of this" — and a
 * modal that closed after each one would make the common case the slow one.
 */
@Composable
private fun AddChild(onAdd: (String) -> Unit) {
    val colors = JarvisTheme.colors
    // `rememberSaveable`, and here it is a real bug rather than tidiness: this
    // row sits below the sub-task list inside a `LazyColumn`, so on a to-do with
    // a description, a few sub-tasks and an activity trail it scrolls off screen
    // easily. With plain `remember` a half-typed sub-task title is discarded
    // when the item is disposed — the text simply vanishes on scrolling back,
    // with nothing to suggest why.
    var text by rememberSaveable { mutableStateOf("") }

    fun submit() {
        if (text.isBlank()) return
        onAdd(text)
        text = ""
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(Corner.Sm)
                .background(colors.surfaceSunk, Corner.Sm)
                .padding(horizontal = Space.x3, vertical = Space.x2),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = LocalTextStyle.current.merge(
                    JarvisTheme.typography.bodyMedium.copy(color = colors.ink),
                ),
                cursorBrush = SolidColor(colors.brandCore),
                singleLine = true,
                // Done rather than a newline: a sub-task title is one line, and
                // the keyboard's action key is the fastest way to add several.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { field ->
                    if (text.isEmpty()) {
                        Text(
                            "Add a sub-task",
                            style = JarvisTheme.typography.bodyMedium,
                            color = colors.inkMuted,
                        )
                    }
                    field()
                },
            )
        }
        TextButton(enabled = text.isNotBlank(), onClick = { submit() }) { Text("Add") }
    }
}

/**
 * The activity trail (§9.4.3).
 *
 * **An unrecognised verb renders as its own raw string rather than vanishing.**
 * That is the promise made to gw03 on tracker 136, and `TodoEvent.verb` being a
 * `String` is what makes it keepable — `coerceInputValues` would otherwise turn
 * an eleventh verb into whichever value happens to be declared first, and a row
 * quietly relabelled is worse than one that reads oddly.
 *
 * Only the first page is shown. A to-do with two hundred events has them because
 * something went wrong with it, and the tail is not where that answer is.
 */
@Composable
private fun Trail(history: LoadState<List<TodoEvent>>) {
    val colors = JarvisTheme.colors

    when (history) {
        is LoadState.Loading -> Box(
            Modifier
                .fillMaxWidth()
                .padding(Space.x4),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = colors.brandCore) }

        // Says the trail could not be read, rather than that nothing happened.
        // An empty trail is impossible — every to-do has a `created` event — so
        // an empty state here would be a claim that is never true.
        is LoadState.Failed -> Text(
            "The activity trail could not be loaded.",
            style = JarvisTheme.typography.bodySmall,
            color = colors.inkMuted,
            modifier = Modifier.padding(horizontal = Space.Gutter),
        )

        is LoadState.Ready -> Column(Modifier.padding(horizontal = Space.Gutter)) {
            if (history.data.isEmpty()) {
                Text(
                    "Nothing recorded yet.",
                    style = JarvisTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            for (event in history.data) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.x1),
                    horizontalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    Text(
                        text = event.sentence(),
                        style = JarvisTheme.typography.bodySmall,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        // The server's own local day (§3.2), never derived from
                        // the instant here.
                        text = event.localDay.format(DAY),
                        style = JarvisTheme.typography.bodySmall,
                        color = colors.inkMuted,
                    )
                }
            }
        }
    }
}

/**
 * One event as a sentence.
 *
 * The detail is folded in where it exists and left out where it does not:
 * `changes` is absent on every event recorded before 2026-09-01, because the old
 * value was overwritten in place and no copy was kept. That is *unrecoverable*
 * rather than *unrecorded*, so the sentence falls back to the field names and
 * then to the verb alone, instead of claiming nothing changed.
 */
private fun TodoEvent.sentence(): String {
    val verbText = when (known) {
        TodoVerb.Created -> "Created"
        TodoVerb.Moved -> "Deadline moved"
        TodoVerb.Renamed -> "Renamed"
        TodoVerb.Tagged -> "Tagged"
        TodoVerb.Untagged -> "Untagged"
        TodoVerb.Linked -> "Linked to a reminder"
        TodoVerb.Unlinked -> "Unlinked from a reminder"
        TodoVerb.Done -> "Marked done"
        TodoVerb.Cancelled -> "Cancelled"
        TodoVerb.Reopened -> "Reopened"
        // The gateway knows a verb this build does not. Shown rather than
        // dropped: the raw word is still true, and a missing row is not.
        null -> verb
    }

    val d = detail ?: return verbText
    val extra = when (known) {
        TodoVerb.Tagged, TodoVerb.Untagged -> d.tag
        TodoVerb.Renamed, TodoVerb.Moved ->
            d.changes?.joinToString(", ") { change ->
                change.field + ": " + (change.before ?: "nothing") +
                    " to " + (change.after ?: "nothing")
            } ?: d.fields?.joinToString(", ")
        else -> null
    }
    return if (extra.isNullOrBlank()) verbText else verbText + " — " + extra
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
