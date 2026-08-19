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
import com.ar13x.jarvis.core.ui.DueDateFormat
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.motion.motionFloat
import com.ar13x.jarvis.designsystem.motion.motionSize
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
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
                            .padding(end = ACTIONS_RESERVE),
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
                            Text(
                                text = task.description,
                                style = JarvisTheme.typography.bodyMedium,
                                color = colors.inkMuted,
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
        Text(
            text = if (showNextFire && task.nextFireAt != null) {
                DueDateFormat.nextFire(task.nextFireAt)
            } else {
                DueDateFormat.forRow(task)
            },
            // Tabular numerals so a column of times does not read ragged (§6.6).
            style = JarvisTheme.typography.bodySmall.tabularNums(),
            color = colors.inkMuted,
        )
        // Straight from the server's `due_today`. Never computed (plan §3.2).
        if (task.dueToday && !task.status.isTerminal) {
            DueTodayPill()
        }
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
