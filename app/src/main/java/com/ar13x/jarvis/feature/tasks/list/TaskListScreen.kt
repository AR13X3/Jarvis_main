package com.ar13x.jarvis.feature.tasks.list

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Info
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import androidx.compose.material.icons.automirrored.rounded.Chat
import com.ar13x.jarvis.core.model.SessionSummary
import androidx.compose.ui.text.style.TextOverflow
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.component.JarvisChip
import com.ar13x.jarvis.designsystem.component.ScreenHeader
import com.ar13x.jarvis.feature.update.UpdateBanner
import com.ar13x.jarvis.designsystem.component.SectionHeader
import com.ar13x.jarvis.designsystem.component.softShadow
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.motion.motionFloat
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.designsystem.theme.tabularNums
import com.ar13x.jarvis.reminders.ReminderReadiness
import com.ar13x.jarvis.reminders.ReminderSetupCard
import com.ar13x.jarvis.reminders.rememberReminderReadiness
import com.ar13x.jarvis.reminders.work.OccurrenceRefreshWorker

private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

/** How close to the end before the next page is requested. */
private const val PREFETCH_ROWS = 5

@Composable
fun TaskListScreen(
    onOpenTask: (Long) -> Unit,
    onNewSession: () -> Unit,
    onOpenDraft: (String) -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TaskListScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenTask = onOpenTask,
        onNewSession = onNewSession,
        onOpenDraft = onOpenDraft,
        onAbout = onAbout,
        modifier = modifier,
    )
}

@Composable
fun TaskListScreen(
    state: TaskListUiState,
    onEvent: (TaskListEvent) -> Unit,
    onOpenTask: (Long) -> Unit,
    onNewSession: () -> Unit,
    onOpenDraft: (String) -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    val listState = rememberLazyListState()
    val snackbars = remember { SnackbarHostState() }
    val readiness = rememberReminderReadiness()

    // The gateway owns all state (parent plan §2.1), so the list is only ever a
    // view of it — and it goes stale the moment anything else writes. Confirming
    // a proposal in a session creates a task the list has never heard of, and
    // the scheduler flips tasks to `awaiting` or `incomplete` with no client
    // involved at all. Re-reading on resume is what makes coming back from a
    // session, or from the home screen, show what is actually there.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onEvent(TaskListEvent.Refresh) }

    // Foreground refresh of the alarm window (plan §7.1), alongside the daily
    // WorkManager run. Cheap — one request — and it is the path that actually
    // fires on a Samsung, where periodic work gets deferred.
    val appContext = LocalContext.current.applicationContext
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        OccurrenceRefreshWorker.enqueueNow(appContext)
    }

    UndoSnackbarEffect(state, onEvent, snackbars)
    FailureSnackbarEffect(state.transientFailure, onEvent, snackbars)
    LoadMoreEffect(state, listState, onEvent)

    Box(modifier.fillMaxSize()) {
        BrandBackdrop() {
            when (val content = state.content) {
                is LoadState.Loading -> LoadingScreen(onNewSession, onAbout)
                is LoadState.Failed -> FailureScreen(
                    reason = content.reason,
                    onAbout = onAbout,
                    onRetry = { onEvent(TaskListEvent.Refresh) },
                )
                is LoadState.Ready -> TaskList(
                    content = content.data,
                    state = state,
                    listState = listState,
                    readiness = readiness,
                    onEvent = onEvent,
                    onOpenTask = onOpenTask,
                    onNewSession = onNewSession,
                    onOpenDraft = onOpenDraft,
                    onAbout = onAbout,
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
    readiness: ReminderReadiness,
    onEvent: (TaskListEvent) -> Unit,
    onOpenTask: (Long) -> Unit,
    onNewSession: () -> Unit,
    onOpenDraft: (String) -> Unit,
    onAbout: () -> Unit,
) {
    val showSections = content.showsSections(state.filters)

    // Recomputed whenever the list is, which is on every resume and after every
    // mutation (see the ON_RESUME refresh above). Deliberately not a ticking
    // clock: a section that reshuffles under the thumb while being read is
    // worse than one that is a few minutes stale, and coming back to the app is
    // exactly when it matters.
    val overdue = remember(content) { content.overdue() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Space.x12),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        item(key = "header") {
            TasksHeader(content = content, onNewSession = onNewSession, onAbout = onAbout)
        }

        // Above the reminder card: being on a build the gateway may already
        // have moved past explains failures the reminder card cannot.
        item(key = "update-banner") {
            UpdateBanner(
                modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.x1),
            )
        }

        item(key = "reminder-setup") {
            // Only renders when something is actually missing, and vanishes once
            // it is fixed — asked in context rather than as a launch-time wall
            // of dialogs (plan §5.5).
            //
            // The readiness itself is read above the list, not here: it queries
            // three system services over binder, and doing that inside a lazy
            // item means re-querying on the main thread every time the card
            // scrolls back into view.
            ReminderSetupCard(
                readiness = readiness,
                modifier = Modifier.padding(horizontal = Space.Gutter),
            )
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

            // Above Overdue, and above everything: these are the only rows the
            // task list cannot otherwise reach. An overdue task is at least
            // visible elsewhere; an unconfirmed conversation is not a task at
            // all, so if it is not here it is nowhere.
            if (state.drafts.isNotEmpty()) {
                item(key = "h-drafts") { SectionHeader("Unfinished") }
                items(state.drafts, key = { "draft-" + it.id }) { draft ->
                    DraftRow(
                        draft = draft,
                        onOpen = { onOpenDraft(draft.id) },
                        modifier = Modifier
                            .padding(horizontal = Space.Gutter)
                            .animateItem(),
                    )
                }
            }

            // Above everything, because it is the only section that is already
            // costing you something. A task set for 6:40 must not look
            // indistinguishable from the rest of the list at 6:45.
            if (overdue.isNotEmpty()) {
                item(key = "h-overdue") {
                    SectionHeader(
                        title = if (overdue.size == 1) "Overdue" else "Overdue · " + overdue.size,
                    )
                }
                items(overdue, key = { "overdue-" + it.id }) { task ->
                    // A repeating rule is here because a *firing* was missed,
                    // and its `due_at` is the series anchor — months old and
                    // never the thing you missed. Read it by next fire so the
                    // row names the moment that put it in this section.
                    Row_(
                        task, onOpenTask, onEvent, state, Modifier.animateItem(),
                        showNextFire = task.isRecurring,
                    )
                }
            }

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
        onToggleChecklistItem = { line ->
            onEvent(TaskListEvent.ToggleChecklistItem(task, line))
        },
        modifier = modifier.padding(horizontal = Space.Gutter),
    )
}

@Composable
private fun TasksHeader(
    content: TaskListContent,
    onNewSession: () -> Unit,
    onAbout: () -> Unit,
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
            // A count is the one number on this screen worth reading before the
            // words around it, so it gets its own shape. Zero has no number to
            // show and stays a sentence — a circled 0 would draw the eye to the
            // absence of work, which is the opposite of useful.
            eyebrow = if (dueToday == 0) "Nothing due today" else null,
            eyebrowContent = if (dueToday == 0) {
                null
            } else {
                {
                    DueTodayCount(
                        count = dueToday,
                        label = if (dueToday == 1) "thing due today" else "things due today",
                    )
                }
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
            trailing = {
                // A ghost circle, not a second saturated one: the + is the only
                // saturated element in the list so the eye lands on it (§6.7),
                // and a build-identity screen must never compete with it.
                CircleIconButton(onClick = onAbout, diameter = 40.dp) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = "About this build",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(20.dp),
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
                selected = filters.status == status,
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
private fun LoadingScreen(onNewSession: () -> Unit, onAbout: () -> Unit) {
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
            trailing = {
                // A ghost circle, not a second saturated one: the + is the only
                // saturated element in the list so the eye lands on it (§6.7),
                // and a build-identity screen must never compete with it.
                CircleIconButton(onClick = onAbout, diameter = 40.dp) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = "About this build",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(20.dp),
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
private fun FailureScreen(reason: FailureReason, onAbout: () -> Unit, onRetry: () -> Unit) {
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
            // The header with its About affordance is not on screen in this
            // state, and this is precisely when "which build is that?" gets
            // asked — so the one screen that works with the gateway down keeps
            // a way in.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onAbout) {
                    Text("About this build", color = colors.inkMuted)
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

/**
 * The due-today count, given its own shape.
 *
 * Deliberately **not** brand-filled: §6.7 reserves that for the `+`, which is
 * meant to be the only saturated element in the list. A surface disc reads as
 * raised against the wash without competing with it, and keeps the eye's first
 * stop on the action rather than the tally.
 */
@Composable
private fun DueTodayCount(
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Box(
            Modifier
                .size(30.dp)
                .then(
                    if (colors.isDark) {
                        Modifier.border(1.dp, colors.hairline, CircleShape)
                    } else {
                        Modifier.softShadow(CircleShape, tight = 1.dp, wide = 10.dp, tint = colors.shadowTint)
                    },
                )
                .clip(CircleShape)
                .background(colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                // Tabular so a jump from 9 to 10 does not shift the label beside
                // it, and the disc keeps its optical centre.
                style = JarvisTheme.typography.titleMedium.tabularNums(),
                color = colors.ink,
            )
        }
        Text(
            text = label,
            style = JarvisTheme.typography.titleMedium,
            color = colors.ink.copy(alpha = 0.72f),
        )
    }
}

/**
 * A task conversation that never became a task.
 *
 * Deliberately not a `TaskRow`: it has no status, no due date and no cancel,
 * because it is not a task — it is a conversation that was heading towards one.
 * Dressing it as a task row would promise a thing that does not exist, and the
 * whole point of the section is that these are *not* in the list yet.
 *
 * The title is the server's, derived from the first thing said, which is the
 * only honest label available before a proposal is confirmed.
 */
@Composable
private fun DraftRow(
    draft: SessionSummary,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors

    JarvisCard(modifier = modifier.fillMaxWidth(), onClick = onOpen, contentPadding = Space.x4) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Rounded.Chat,
                contentDescription = null,
                tint = colors.inkMuted,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(Space.x3))
            Column(Modifier.weight(1f)) {
                Text(
                    text = draft.title ?: "Unfinished conversation",
                    style = JarvisTheme.typography.titleMedium,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = draftSubtitle(draft),
                    style = JarvisTheme.typography.bodySmall.tabularNums(),
                    color = colors.inkMuted,
                )
            }
        }
    }
}

private fun draftSubtitle(draft: SessionSummary): String {
    val turns = if (draft.messageCount == 1) "1 message" else draft.messageCount.toString() + " messages"
    return turns + " · not created yet"
}
