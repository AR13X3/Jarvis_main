package com.ar13x.jarvis.feature.tasks.list

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ar13x.jarvis.core.data.message
import com.ar13x.jarvis.core.data.offersTailscale
import com.ar13x.jarvis.core.model.CancelScope
import com.ar13x.jarvis.core.model.FailureReason
import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.model.TaskStatus
import com.ar13x.jarvis.core.ui.LoadState
import com.ar13x.jarvis.designsystem.component.BrandBackdrop
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.component.JarvisChip
import com.ar13x.jarvis.designsystem.component.ScreenHeader
import com.ar13x.jarvis.designsystem.component.SectionHeader
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.motion.motionFloat
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space

private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

/** How close to the end before the next page is requested. */
private const val PREFETCH_ROWS = 5

@Composable
fun TaskListScreen(
    onOpenTask: (Long) -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TaskListScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenTask = onOpenTask,
        onNewSession = onNewSession,
        modifier = modifier,
    )
}

@Composable
fun TaskListScreen(
    state: TaskListUiState,
    onEvent: (TaskListEvent) -> Unit,
    onOpenTask: (Long) -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    val listState = rememberLazyListState()
    val snackbars = remember { SnackbarHostState() }

    UndoSnackbarEffect(state, onEvent, snackbars)
    FailureSnackbarEffect(state.transientFailure, onEvent, snackbars)
    LoadMoreEffect(state, listState, onEvent)

    Box(modifier.fillMaxSize()) {
        BrandBackdrop() {
            when (val content = state.content) {
                is LoadState.Loading -> LoadingScreen(onNewSession)
                is LoadState.Failed -> FailureScreen(content.reason) { onEvent(TaskListEvent.Refresh) }
                is LoadState.Ready -> TaskList(
                    content = content.data,
                    state = state,
                    listState = listState,
                    onEvent = onEvent,
                    onOpenTask = onOpenTask,
                    onNewSession = onNewSession,
                )
            }
        }

        SnackbarHost(
            hostState = snackbars,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Space.Gutter, vertical = Space.x4),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = Corner.Sm,
                containerColor = colors.ink,
                contentColor = colors.ground,
                actionColor = colors.brandCore,
            )
        }
    }

    state.pendingCancel?.let { target ->
        CancelTaskDialog(
            task = target,
            inFlight = state.cancelInFlight,
            onConfirm = { scope -> onEvent(TaskListEvent.ConfirmCancel(scope)) },
            onDismiss = { onEvent(TaskListEvent.DismissCancel) },
        )
    }
}

@Composable
private fun TaskList(
    content: TaskListContent,
    state: TaskListUiState,
    listState: LazyListState,
    onEvent: (TaskListEvent) -> Unit,
    onOpenTask: (Long) -> Unit,
    onNewSession: () -> Unit,
) {
    val showSections = content.showsSections(state.filters)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Space.x12),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        item(key = "header") {
            TasksHeader(content = content, onNewSession = onNewSession)
        }

        item(key = "filters") {
            FilterBar(
                filters = state.filters,
                listState = listState,
                onEvent = onEvent,
            )
        }

        if (showSections) {
            // Sections with no rows are hidden, not shown empty — three empty
            // headers on first launch is noise (plan §5.2).
            if (content.priority.isNotEmpty()) {
                item(key = "h-priority") { SectionHeader("Priority") }
                items(content.priority, key = { "priority-" + it.id }) { task ->
                    Row_(task, onOpenTask, onEvent, state, Modifier.animateItem())
                }
            }
            if (content.recurring.isNotEmpty()) {
                item(key = "h-recurring") { SectionHeader("Recurring") }
                items(content.recurring, key = { "recurring-" + it.id }) { task ->
                    // The recurring section sorts and reads by next fire, which
                    // is what it means for a repeating task to be "next".
                    Row_(task, onOpenTask, onEvent, state, Modifier.animateItem(), showNextFire = true)
                }
            }
            // The All-tasks section always renders, because it carries the
            // primary empty state.
            item(key = "h-all") { SectionHeader("All tasks") }
        }

        if (content.all.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    filtered = !state.filters.isEmpty,
                    onClearFilters = { onEvent(TaskListEvent.ClearFilters) },
                )
            }
        } else {
            items(content.all, key = { "all-" + it.id }) { task ->
                Row_(task, onOpenTask, onEvent, state, Modifier.animateItem())
            }
        }

        if (content.hasMore || state.appending || state.appendFailure != null) {
            item(key = "append") {
                AppendFooter(
                    appending = state.appending,
                    failure = state.appendFailure,
                    onRetry = { onEvent(TaskListEvent.RetryAppend) },
                )
            }
        }
    }
}

/** Shorthand so the four call sites above stay readable. */
@Composable
private fun Row_(
    task: Task,
    onOpenTask: (Long) -> Unit,
    onEvent: (TaskListEvent) -> Unit,
    state: TaskListUiState,
    modifier: Modifier = Modifier,
    showNextFire: Boolean = false,
) {
    TaskRow(
        task = task,
        onOpen = { onOpenTask(task.id) },
        onTogglePriority = { onEvent(TaskListEvent.TogglePriority(task)) },
        onCancel = { onEvent(TaskListEvent.RequestCancel(task)) },
        showNextFire = showNextFire,
        expanded = task.id in state.expandedIds,
        onToggleExpand = { onEvent(TaskListEvent.ToggleExpand(task.id)) },
        modifier = modifier.padding(horizontal = Space.Gutter),
    )
}

@Composable
private fun TasksHeader(
    content: TaskListContent,
    onNewSession: () -> Unit,
) {
    val colors = JarvisTheme.colors
    // Counted from the server's `due_today`, never derived from an instant.
    val dueToday = remember(content) {
        (content.priority + content.recurring + content.all)
            .distinctBy { it.id }
            .count { it.dueToday && !it.status.isTerminal }
    }

    Column {
        ScreenHeader(
            eyebrow = when (dueToday) {
                0 -> "Nothing due today"
                1 -> "1 thing due today"
                else -> dueToday.toString() + " things due today"
            },
            headline = "What needs\ndoing today?",
            centred = true,
            leading = {
                // The + is a brand-filled circle and the only saturated element
                // in the list, so the eye lands on it immediately (plan §6.7).
                CircleIconButton(
                    onClick = onNewSession,
                    diameter = 44.dp,
                    background = colors.brandCore,
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "New task",
                        tint = colors.onBrand,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )
        Spacer(Modifier.height(Space.x6))
    }
}

/**
 * Filter chips that collapse on scroll (plan §5.2).
 *
 * Fading and shrinking rather than unmounting: a row that vanished outright
 * would make the list jump under the thumb at the exact moment the user is
 * moving it.
 */
@Composable
private fun FilterBar(
    filters: TaskFilters,
    listState: LazyListState,
    onEvent: (TaskListEvent) -> Unit,
) {
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val collapse by animateFloatAsState(
        targetValue = if (scrolled && filters.isEmpty) 0f else 1f,
        animationSpec = motionFloat(Motion.Standard),
        label = "filterCollapse",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = collapse
                scaleY = 0.85f + 0.15f * collapse
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            }
            .height((44 * collapse).dp.coerceAtLeast(0.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.Gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        DateRange.entries.forEach { range ->
            if (range != DateRange.Any || filters.range != DateRange.Any) {
                JarvisChip(
                    label = range.label,
                    selected = filters.range == range,
                    onClick = { onEvent(TaskListEvent.SetRange(range)) },
                )
            }
        }
        TaskStatus.entries.forEach { status ->
            JarvisChip(
                label = statusLabel(status),
                selected = status in filters.statuses,
                onClick = { onEvent(TaskListEvent.ToggleStatusFilter(status)) },
            )
        }
    }
}

private fun statusLabel(status: TaskStatus) = when (status) {
    TaskStatus.Active -> "Active"
    TaskStatus.Awaiting -> "Waiting"
    TaskStatus.Completed -> "Done"
    TaskStatus.Cancelled -> "Cancelled"
    // A lapse, not a failure — the label should not read as an accusation.
    TaskStatus.Incomplete -> "Missed"
}

@Composable
private fun EmptyState(
    filtered: Boolean,
    onClearFilters: () -> Unit,
) {
    val colors = JarvisTheme.colors
    JarvisCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter),
        contentPadding = Space.x6,
    ) {
        Text(
            text = if (filtered) "Nothing matches these filters" else "No tasks yet — tap + to start",
            style = JarvisTheme.typography.titleMedium,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (filtered) {
            Spacer(Modifier.height(Space.x3))
            TextButton(onClick = onClearFilters, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Clear filters", color = colors.brandCore, style = JarvisTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AppendFooter(
    appending: Boolean,
    failure: FailureReason?,
    onRetry: () -> Unit,
) {
    val colors = JarvisTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            failure != null -> TextButton(onClick = onRetry) {
                Text(
                    text = failure.message() + " Tap to retry.",
                    style = JarvisTheme.typography.bodySmall,
                    color = colors.brandCore,
                    textAlign = TextAlign.Center,
                )
            }
            appending -> CircularProgressIndicator(
                color = colors.brandCore,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LoadingScreen(onNewSession: () -> Unit) {
    val colors = JarvisTheme.colors
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            eyebrow = " ",
            headline = "What needs\ndoing today?",
            centred = true,
            leading = {
                CircleIconButton(onClick = onNewSession, diameter = 44.dp, background = colors.brandCore) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "New task",
                        tint = colors.onBrand,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )
        Spacer(Modifier.height(Space.x12))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = colors.brandCore,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Names the actual cause (plan §8.2). "Something went wrong" is close to useless
 * when the most likely failure by a wide margin is the phone being off the
 * tailnet and the fix is one toggle — so that case gets a button.
 */
@Composable
private fun FailureScreen(reason: FailureReason, onRetry: () -> Unit) {
    val colors = JarvisTheme.colors
    val context = LocalContext.current

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        JarvisCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.Gutter),
            contentPadding = Space.x6,
        ) {
            Text(
                text = reason.message(),
                style = JarvisTheme.typography.titleMedium,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.x4))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (reason.offersTailscale) {
                    val launch = remember {
                        context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
                    }
                    if (launch != null) {
                        TextButton(onClick = {
                            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) {
                            Text("Open Tailscale", color = colors.brandCore)
                        }
                    }
                }
                TextButton(onClick = onRetry) {
                    Text("Try again", color = colors.brandCore)
                }
            }
        }
    }
}

/**
 * A mis-tap here is as destructive as a wrong model call, so cancel asks —
 * naming the task, because "Are you sure?" over a list of forty is not a
 * question anyone can answer (plan §5.2).
 *
 * **A recurring task gets two answers, not one.** The parent row is a rule and
 * each firing is an occurrence with its own status (parent plan §2.5), so
 * "skip this week" and "stop doing this" are different intentions. Collapsing
 * them into one button means the destructive reading wins by default, which is
 * exactly the wrong default for the irreversible one.
 */
@Composable
private fun CancelTaskDialog(
    task: Task,
    inFlight: Boolean,
    onConfirm: (CancelScope) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = JarvisTheme.colors
    val recurring = task.isRecurring

    AlertDialog(
        onDismissRequest = { if (!inFlight) onDismiss() },
        shape = Corner.Lg,
        containerColor = colors.surface,
        title = {
            Text(
                text = if (recurring) "Cancel this reminder?" else "Cancel this task?",
                style = JarvisTheme.typography.headlineSmall,
                color = colors.ink,
            )
        },
        text = {
            Column {
                Text(
                    text = if (recurring) {
                        "“" + task.title + "” repeats " +
                            (task.recurrenceText ?: "on a schedule").replaceFirstChar { it.lowercase() } + "."
                    } else {
                        // The row is not removed — it stays, cancelled. Saying so
                        // here means the user is not surprised when it is still
                        // on screen.
                        "“" + task.title + "” will be marked cancelled. It stays in your list."
                    },
                    style = JarvisTheme.typography.bodyMedium,
                    color = colors.inkMuted,
                )
                if (recurring) {
                    Spacer(Modifier.height(Space.x4))
                    ScopeChoice(
                        label = "Just this one",
                        detail = "Skips the next occurrence. The reminder keeps repeating.",
                        enabled = !inFlight,
                        onClick = { onConfirm(CancelScope.Occurrence) },
                    )
                    Spacer(Modifier.height(Space.x2))
                    ScopeChoice(
                        label = "The whole series",
                        detail = "Stops it repeating. The task is marked cancelled.",
                        enabled = !inFlight,
                        onClick = { onConfirm(CancelScope.Series) },
                    )
                }
            }
        },
        confirmButton = {
            if (!recurring) {
                TextButton(onClick = { onConfirm(CancelScope.Series) }, enabled = !inFlight) {
                    Text("Cancel task", color = colors.brandCore, style = JarvisTheme.typography.labelLarge)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inFlight) {
                Text("Keep it", color = colors.inkMuted, style = JarvisTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
private fun ScopeChoice(
    label: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = JarvisTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Corner.Sm)
            .background(colors.surfaceSunk, Corner.Sm)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.x4, vertical = Space.x3),
    ) {
        Text(label, style = JarvisTheme.typography.labelLarge, color = colors.ink)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = JarvisTheme.typography.bodySmall, color = colors.inkMuted)
    }
}

@Composable
private fun UndoSnackbarEffect(
    state: TaskListUiState,
    onEvent: (TaskListEvent) -> Unit,
    snackbars: SnackbarHostState,
) {
    val undo = state.undo
    LaunchedEffect(undo) {
        if (undo == null) return@LaunchedEffect
        val result = snackbars.showSnackbar(
            message = if (undo.previous) {
                "Removed priority from “" + undo.title + "”"
            } else {
                "“" + undo.title + "” set as priority"
            },
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
            withDismissAction = false,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> onEvent(TaskListEvent.Undo)
            SnackbarResult.Dismissed -> onEvent(TaskListEvent.DismissUndo)
        }
    }
}

@Composable
private fun FailureSnackbarEffect(
    failure: FailureReason?,
    onEvent: (TaskListEvent) -> Unit,
    snackbars: SnackbarHostState,
) {
    LaunchedEffect(failure) {
        if (failure == null) return@LaunchedEffect
        snackbars.showSnackbar(message = failure.message(), duration = SnackbarDuration.Short)
        onEvent(TaskListEvent.DismissFailure)
    }
}

@Composable
private fun LoadMoreEffect(
    state: TaskListUiState,
    listState: LazyListState,
    onEvent: (TaskListEvent) -> Unit,
) {
    LaunchedEffect(listState, state.content) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= layout.totalItemsCount - PREFETCH_ROWS
        }.collect { nearEnd ->
            if (nearEnd) onEvent(TaskListEvent.LoadMore)
        }
    }
}
