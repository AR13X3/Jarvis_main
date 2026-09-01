package com.ar13x.jarvis.feature.tasks.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.core.model.Task
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.text.style.TextDecoration
import com.ar13x.jarvis.core.ui.ChecklistItem
import com.ar13x.jarvis.core.ui.checklistItems
import com.ar13x.jarvis.core.ui.DueDateFormat
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.motion.motionFloat
import com.ar13x.jarvis.designsystem.motion.motionSize
import com.ar13x.jarvis.designsystem.motion.sharedTaskTitle
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.core.model.TaskStatus
import com.ar13x.jarvis.designsystem.theme.statusStyle
import com.ar13x.jarvis.designsystem.theme.tabularNums

/**
 * Row anatomy per plan §5.2: status indicator · title · due date · due-today dot
 * · recurrence text (if any) · overflow menu · cancel button.
 *
 * ### Layout
 *
 * Three controls scattered around a card read as clutter, so they are grouped
 * into exactly **two** places and nothing floats:
 *
 * ```
 * ┌──────────────────────────────────────────┐
 * │ ●  Title, up to two lines         ⋮   ✕  │   <- actions, anchored top-right
 * │    ★  Today, 9:00 am   ● Today           │
 * │    ↻  Every Friday                       │
 * │    ────────────────────────              │
 * │    Description, when expanded            │
 * │                  ⌄                       │   <- expand, centred on the edge
 * └──────────────────────────────────────────┘
 * ```
 *
 * The expand affordance sits centred on the bottom edge rather than beside the
 * actions because it does something categorically different: the actions change
 * the task, the chevron only changes what you can see of it. Putting it with
 * them invites the exact mis-tap the cancel dialog exists to catch.
 *
 * A card on `Surface` separated by spacing, not a full-bleed list item with a
 * divider hairline (§6.7).
 */
@Composable
fun TaskRow(
    task: Task,
    onOpen: () -> Unit,
    onTogglePriority: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    showNextFire: Boolean = false,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onToggleChecklistItem: (Int) -> Unit = {},
) {
    val colors = JarvisTheme.colors
    val style = statusStyle(task.status)

    // A long title is clamped to two lines and ellipsised, and expanding
    // unclamps it. Whether it actually overflowed is not guessable from length —
    // it depends on the font, the width and where the words break — so it is
    // measured, and the expand affordance appears only when something really is
    // hidden.
    var titleOverflows by remember(task.id) { mutableStateOf(false) }
    val canExpand = titleOverflows || task.description.isNotBlank()

    JarvisCard(
        modifier = modifier.fillMaxWidth().alpha(style.rowAlpha),
        onClick = onOpen,
        containerColor = if (style.rowFill == Color.Transparent) colors.surface else style.rowFill,
        contentPadding = Space.x4,
    ) {
        // The actions are overlaid rather than laid out beside the content, so
        // only the *title* has to make room for them. Everything below —
        // metadata, a long recurrence description — gets the card's full width
        // instead of being squeezed into a column 68dp narrower than it looks.
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    StatusIndicator(
                        color = style.accent,
                        outlined = style.indicatorOutlined,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.width(Space.x3))
                    Text(
                        text = task.title,
                        style = JarvisTheme.typography.titleMedium,
                        color = style.titleColor,
                        textDecoration = style.titleDecoration,
                        maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_TITLE_LINES,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { layout ->
                            if (!expanded) titleOverflows = layout.hasVisualOverflow
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = ACTIONS_RESERVE)
                            // The other end of the row -> session morph (§6.5).
                            // Both ends key off the task id, so the title tracks
                            // between screens instead of cross-fading.
                            .sharedTaskTitle(task.id),
                    )
                }

                Column(Modifier.padding(start = CONTENT_INDENT)) {
                    Spacer(Modifier.height(Space.x2))

                    MetaLine(task = task, showNextFire = showNextFire)

                    if (task.recurrenceText != null) {
                        Spacer(Modifier.height(Space.x1))
                        RecurrenceLine(task.recurrenceText)
                    }

                    AnimatedVisibility(
                        visible = expanded && task.description.isNotBlank(),
                        enter = expandVertically(motionSize(Motion.GentleSize)) +
                            fadeIn(motionFloat(Motion.Standard)),
                        exit = shrinkVertically(motionSize(Motion.GentleSize)) +
                            fadeOut(motionFloat(Motion.Snappy)),
                    ) {
                        Column {
                            Spacer(Modifier.height(Space.x3))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.hairline),
                            )
                            Spacer(Modifier.height(Space.x3))
                            // A description that contains checkboxes becomes a
                            // list you can tick. Everything else still renders
                            // as the prose it is — the two are not exclusive,
                            // and a description is usually both.
                            DescriptionBody(
                                description = task.description,
                                onToggleItem = onToggleChecklistItem,
                            )
                        }
                    }
                }
            }

            // Anchored into the card's top-right corner so the pair reads as one
            // cluster belonging to the row, rather than two loose icons.
            RowActions(
                task = task,
                onTogglePriority = onTogglePriority,
                onCancel = onCancel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = Space.x2, y = -Space.x2),
            )
        }

        if (canExpand) {
            ExpandHandle(
                expanded = expanded,
                onClick = onToggleExpand,
                modifier = Modifier.offset(y = Space.x1),
            )
        }
    }
}

@Composable
private fun RowActions(
    task: Task,
    onTogglePriority: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    var menuOpen by remember { mutableStateOf(false) }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box {
            CircleIconButton(
                onClick = { menuOpen = true },
                diameter = ACTION_SIZE,
                background = Color.Transparent,
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "More options for " + task.title,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = Corner.Sm,
                containerColor = colors.surface,
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (task.isPriority) "Remove priority" else "Set as priority",
                            style = JarvisTheme.typography.bodyMedium,
                            color = colors.ink,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onTogglePriority()
                    },
                )
            }
        }

        // Terminal tasks cannot be cancelled again — only `completed` and
        // `cancelled` lock a task, and `incomplete` deliberately keeps its full
        // mutation set (plan §4.3). The slot is held so rows stay aligned.
        if (task.status.isTerminal) {
            Spacer(Modifier.width(ACTION_SIZE))
        } else {
            CircleIconButton(
                onClick = onCancel,
                diameter = ACTION_SIZE,
                background = Color.Transparent,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Cancel " + task.title,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MetaLine(
    task: Task,
    showNextFire: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        // The star sits with the metadata rather than beside the title: on a
        // title that wraps it would otherwise be pushed to the far right edge
        // and read as belonging to the action cluster instead of the task.
        if (task.isPriority) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = "Priority",
                tint = colors.brandCore,
                modifier = Modifier.size(13.dp),
            )
        }
        // Ask the task, not the display mode. This used to read
        // `!showNextFire && …` on the reasoning that a next fire is never in
        // the past — which is not true in the one window that matters: between
        // a firing passing and the scheduler noticing, exactly the gap the
        // Overdue section exists to cover. `isOverdue` already measures a
        // repeating task against its firing rather than its anchor.
        val overdue = task.isOverdue()

        Text(
            text = if (showNextFire && task.nextFireAt != null) {
                DueDateFormat.nextFire(task.nextFireAt)
            } else {
                DueDateFormat.forRow(task)
            },
            // Tabular numerals so a column of times does not read ragged (§6.6).
            style = JarvisTheme.typography.bodySmall.tabularNums(),
            // Amber, not red: a missed deadline needs attention and is not a
            // failure, and the brand hue cannot also mean "bad" (§6.2).
            color = if (overdue) colors.status.incomplete else colors.inkMuted,
        )
        if (overdue) {
            OverduePill()
        }
        // Says it in words, not just as a coloured dot.
        //
        // `cancelled` and `incomplete` were distinguishable only by a
        // strike-through and whether a 7dp dot was filled or outlined -- a real
        // distinction that nobody reads. They are different in kind: one is a
        // decision, the other is a thing that did not get done, and §6.2 keeps
        // both statuses precisely so that difference survives. A row that will
        // not say which is throwing that away at the last step.
        if (task.status == TaskStatus.Incomplete || task.status == TaskStatus.Cancelled) {
            StatusPill(task.status)
        }
        // Straight from the server's `due_today`. Never computed (plan §3.2).
        // Suppressed once overdue: "Today" beside "Overdue" reads as a
        // contradiction, and the later fact is the one that matters.
        if (task.dueToday && !task.status.isTerminal && !overdue) {
            DueTodayPill()
        }
    }
}

/**
 * Says it plainly on the row, so the list is readable without opening anything.
 *
 * Form as well as colour — a filled pill, not just amber text — so it survives
 * colour-blindness and a dark theme, the same reasoning §6.2 applies to the
 * cancelled/incomplete distinction.
 */
/**
 * The status, in words, for the two that are easy to confuse.
 *
 * Amber and outlined for `incomplete`; grey and quiet for `cancelled` -- form
 * and colour, so the distinction survives a dark theme and colour-blindness,
 * which is the same rule `StatusStyle` states and this row was not honouring.
 */
@Composable
private fun StatusPill(status: TaskStatus, modifier: Modifier = Modifier) {
    val colors = JarvisTheme.colors
    val style = statusStyle(status)
    Box(
        modifier
            .clip(Corner.Pill)
            .background(style.accent.copy(alpha = 0.16f), Corner.Pill)
            .padding(horizontal = Space.x2, vertical = 1.dp),
    ) {
        Text(
            text = style.label,
            style = JarvisTheme.typography.labelSmall,
            color = style.accent,
        )
    }
}

@Composable
private fun OverduePill(modifier: Modifier = Modifier) {
    val colors = JarvisTheme.colors
    Box(
        modifier
            .clip(Corner.Pill)
            .background(colors.status.incomplete.copy(alpha = 0.16f), Corner.Pill)
            .padding(horizontal = Space.x2, vertical = 1.dp),
    ) {
        Text(
            text = "Overdue",
            style = JarvisTheme.typography.labelSmall,
            color = colors.status.incomplete,
        )
    }
}

/**
 * A description, with its checkbox lines made tappable.
 *
 * The prose around them is untouched and still shown — descriptions are
 * typically a sentence *and* a list, as the one that prompted this feature was:
 * "apply for the warehouse job again and other Christmas casual jobs" followed
 * by the places to apply.
 *
 * Ticking rewrites only that line's marker (see `Checklist.kt`), so nothing the
 * agent wrote is reformatted in order to tick a box.
 */
@Composable
private fun DescriptionBody(
    description: String,
    onToggleItem: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    val items = remember(description) { description.checklistItems() }

    if (items.isEmpty()) {
        Text(
            text = description,
            style = JarvisTheme.typography.bodyMedium,
            color = colors.inkMuted,
            modifier = modifier,
        )
        return
    }

    val checklistLines = remember(items) { items.map { it.line }.toSet() }
    val prose = remember(description, checklistLines) {
        description.lines()
            .filterIndexed { index, _ -> index !in checklistLines }
            .joinToString(NEWLINE)
            .trim()
    }

    Column(modifier) {
        if (prose.isNotBlank()) {
            Text(
                text = prose,
                style = JarvisTheme.typography.bodyMedium,
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(Space.x3))
        }

        val doneCount = items.count { it.done }
        Text(
            text = "$doneCount of ${items.size} done",
            style = JarvisTheme.typography.labelSmall,
            color = colors.inkMuted,
        )
        Spacer(Modifier.height(Space.x2))

        items.forEach { item ->
            ChecklistRow(item = item, onToggle = { onToggleItem(item.line) })
        }
    }
}

@Composable
private fun ChecklistRow(item: ChecklistItem, onToggle: () -> Unit) {
    val colors = JarvisTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Corner.Sm)
            .clickable(onClick = onToggle)
            // A real touch target. A 16dp checkbox on a phone is a mis-tap
            // waiting to happen, and a mis-tap here edits the task.
            .padding(vertical = Space.x2, horizontal = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(Corner.Xs)
                .background(
                    if (item.done) colors.brandCore else colors.surfaceSunk,
                    Corner.Xs,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.onBrand,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.width(Space.x3))
        Text(
            text = item.text,
            style = JarvisTheme.typography.bodyMedium,
            // Struck and muted when done, so the state survives colour-blindness
            // and a dark theme — the same form-as-well-as-colour rule §6.2
            // applies to cancelled versus incomplete.
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            color = if (item.done) colors.inkMuted else colors.ink,
        )
    }
}

@Composable
private fun RecurrenceLine(text: String, modifier: Modifier = Modifier) {
    val colors = JarvisTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Icon(
            Icons.Rounded.Repeat,
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier.size(13.dp),
        )
        // Server-rendered by describe_recurrence(). The app owns no recurrence
        // library and parses no RRULE (plan §3.1).
        // Two lines, because a real recurrence description runs long — "Every
        // Monday, Wednesday and Friday" clipped to "Every Monday, Wednesday
        // and…" loses the one day you needed to check.
        Text(
            text = text,
            style = JarvisTheme.typography.bodySmall,
            color = colors.inkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Centred on the card's bottom edge — unambiguous "there is more below". */
@Composable
private fun ExpandHandle(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motionFloat(Motion.Snappy),
        label = "expandChevron",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(Corner.Sm)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "Hide details" else "Show details",
            tint = colors.inkMuted,
            modifier = Modifier.size(20.dp).rotate(rotation),
        )
    }
}

/**
 * Form as well as colour (plan §6.2): `incomplete` is an outlined ring against
 * every other status's filled dot, so the distinction survives colour-blindness
 * and a dark theme.
 */
@Composable
private fun StatusIndicator(
    color: Color,
    outlined: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(10.dp)
            .clip(CircleShape)
            .then(
                if (outlined) {
                    Modifier.border(2.dp, color, CircleShape)
                } else {
                    Modifier.background(color, CircleShape)
                },
            ),
    )
}

@Composable
private fun DueTodayPill(modifier: Modifier = Modifier) {
    val colors = JarvisTheme.colors
    Row(
        modifier = modifier
            .clip(Corner.Pill)
            .background(colors.brandTint, Corner.Pill)
            .padding(horizontal = Space.x2, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.brandCore, CircleShape),
        )
        Text(
            text = "Today",
            style = JarvisTheme.typography.labelSmall,
            color = if (colors.isDark) colors.ink else colors.brandDeep,
        )
    }
}

/**
 * Collapsed rows clamp to two lines. One line loses too much of a real task
 * title; three lets a single verbose task dominate the list.
 */
private const val COLLAPSED_TITLE_LINES = 2

private val ACTION_SIZE = 34.dp

/** Width the title yields to the overlaid action cluster. */
private val ACTIONS_RESERVE = 62.dp

/** Status dot plus its gutter, so metadata lines up under the title. */
private val CONTENT_INDENT = 22.dp

/** A literal newline. */
private const val NEWLINE = "\n"
