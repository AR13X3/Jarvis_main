package com.ar13x.jarvis.feature.conversation

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.ProposalAction
import com.ar13x.jarvis.core.model.ProposalStatus
import com.ar13x.jarvis.core.ui.DueDateFormat
import com.ar13x.jarvis.designsystem.component.softShadow
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.motion.motionColor
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.designsystem.theme.tabularNums

/**
 * The confirmation card — where every write in the app happens (parent plan
 * §2.4). Rendered **inline in the stream, not as a dialog** (plan §5.3), so it
 * reads as part of the conversation that produced it.
 *
 * Two things here are load-bearing rather than decorative:
 *
 * 1. **`recurrence_text` is never collapsed behind a "details" affordance.**
 *    The parent plan measured the model emitting malformed tool arguments about
 *    1 in 5 on the "last Friday of every month" pattern (§2.5). A wrong day set
 *    is only catchable by a human reading it *before* confirming, so it is shown
 *    at full size next to everything else.
 * 2. **A resolved card stays.** It settles into its resolved state in place
 *    rather than vanishing, because scrolling back should show what you agreed
 *    to, not a blank.
 */
@Composable
fun ConfirmCard(
    component: AgentComponent.Confirm,
    resolving: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    val summary = component.summary
    val resolved = component.status.isResolved

    // Resolved cards fall back to the quiet surface; a live one keeps the brand
    // edge, because it is the only thing on screen asking for a decision.
    val border by animateColorAsState(
        targetValue = if (resolved) colors.hairline else colors.brandCore.copy(alpha = 0.45f),
        animationSpec = motionColor(Motion.StandardColor),
        label = "confirmBorder",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(Corner.Lg, tight = 2.dp, wide = 16.dp, tint = colors.shadowTint)
            .clip(Corner.Lg)
            .background(colors.surface, Corner.Lg)
            .border(1.dp, border, Corner.Lg)
            .padding(Space.x4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionBadge(action = summary.action, resolved = resolved)
            Spacer(Modifier.width(Space.x3))
            Text(
                text = actionLabel(summary.action),
                style = JarvisTheme.typography.labelSmall,
                color = colors.inkMuted,
            )
            Spacer(Modifier.weight(1f))
            if (resolved) ResolutionChip(component.status)
        }

        Spacer(Modifier.height(Space.x3))

        Text(
            text = summary.title,
            style = JarvisTheme.typography.headlineSmall,
            color = colors.ink,
        )

        val whenText = DueDateFormat.forProposal(summary.dueDate, summary.dueAt)
        if (whenText != null) {
            Spacer(Modifier.height(Space.x2))
            DetailLine(icon = Icons.Rounded.Schedule, text = whenText, tabular = true)
        }

        // Prominent, always. See the class doc — this is the line that catches a
        // misparsed recurrence before it becomes a task that misfires weekly.
        if (summary.recurrenceText != null) {
            Spacer(Modifier.height(Space.x2))
            DetailLine(
                icon = Icons.Rounded.Repeat,
                text = summary.recurrenceText,
                emphasis = true,
            )
        }

        if (summary.isPriority) {
            Spacer(Modifier.height(Space.x2))
            DetailLine(icon = Icons.Rounded.Star, text = "Priority", tint = colors.brandCore)
        }

        if (summary.description.isNotBlank()) {
            Spacer(Modifier.height(Space.x3))
            Text(
                text = summary.description,
                style = JarvisTheme.typography.bodyMedium,
                color = colors.inkMuted,
            )
        }

        if (!resolved) {
            Spacer(Modifier.height(Space.x4))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                ConfirmAction(
                    label = "Confirm",
                    filled = true,
                    loading = resolving,
                    enabled = !resolving,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
                ConfirmAction(
                    label = "Reject",
                    filled = false,
                    loading = false,
                    enabled = !resolving,
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun actionLabel(action: ProposalAction) = when (action) {
    ProposalAction.Create -> "New task"
    ProposalAction.Update -> "Change"
    ProposalAction.Cancel -> "Cancel"
    ProposalAction.Complete -> "Complete"
}

@Composable
private fun ActionBadge(action: ProposalAction, resolved: Boolean) {
    val colors = JarvisTheme.colors
    val icon: ImageVector = when (action) {
        ProposalAction.Create -> Icons.Rounded.TaskAlt
        ProposalAction.Update -> Icons.Rounded.EditCalendar
        ProposalAction.Cancel -> Icons.Rounded.Block
        ProposalAction.Complete -> Icons.Rounded.Check
    }
    val tint = if (resolved) colors.inkMuted else colors.brandCore
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (resolved) colors.surfaceSunk else colors.brandTint,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ResolutionChip(status: ProposalStatus) {
    val colors = JarvisTheme.colors
    val (label, tint) = when (status) {
        ProposalStatus.Confirmed -> "Confirmed" to colors.status.completed
        ProposalStatus.Rejected -> "Rejected" to colors.status.cancelled
        ProposalStatus.Superseded -> "Superseded" to colors.status.cancelled
        ProposalStatus.Pending -> "" to colors.inkMuted
    }
    Row(
        modifier = Modifier
            .clip(Corner.Pill)
            .background(colors.surfaceSunk, Corner.Pill)
            .padding(horizontal = Space.x3, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        if (status == ProposalStatus.Confirmed) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(label, style = JarvisTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun DetailLine(
    icon: ImageVector,
    text: String,
    tint: Color = JarvisTheme.colors.inkMuted,
    tabular: Boolean = false,
    emphasis: Boolean = false,
) {
    val colors = JarvisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        val style = if (emphasis) {
            JarvisTheme.typography.bodyLarge
        } else {
            JarvisTheme.typography.bodyMedium
        }
        Text(
            text = text,
            style = if (tabular) style.tabularNums() else style,
            color = if (emphasis) colors.ink else colors.inkMuted,
        )
    }
}

@Composable
private fun ConfirmAction(
    label: String,
    filled: Boolean,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(Corner.Cta)
            .background(if (filled) colors.brandCore else colors.surfaceSunk, Corner.Cta)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = colors.onBrand,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = label,
                style = JarvisTheme.typography.labelLarge,
                color = if (filled) colors.onBrand else colors.ink,
            )
        }
    }
}
