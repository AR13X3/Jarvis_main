package com.ar13x.jarvis.feature.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.TaskOption
import com.ar13x.jarvis.core.ui.DueDateFormat
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.motion.motionFloat
import com.ar13x.jarvis.designsystem.motion.motionSize
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.designsystem.theme.statusStyle
import com.ar13x.jarvis.designsystem.theme.tabularNums

/**
 * Disambiguation, rendered as **buttons, three at a time** (plan §5.4).
 *
 * The date is the disambiguation key, which is why exact timestamps are stored
 * at all (parent plan §2.7) — so it is given equal weight to the title rather
 * than tucked underneath as secondary text.
 *
 * Three is the plan's number and worth keeping: the flow exists because the
 * agent could not tell several same-titled tasks apart, and answering that with
 * a wall of twelve identical titles just moves the problem onto the user.
 */
@Composable
fun TaskOptionsCard(
    component: AgentComponent.TaskOptions,
    extra: List<TaskOption>,
    cursor: String?,
    loadingMore: Boolean,
    onPick: (Long) -> Unit,
    onShowMore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    // The card starts with what the turn returned and grows as "Show more"
    // appends; the appended pages live in UI state so they survive scrolling.
    val options = component.options + extra
    val nextCursor = cursor ?: component.moreCursor

    Column(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            if (index > 0) Spacer(Modifier.height(Space.x2))
            AnimatedVisibility(
                visible = true,
                enter = expandVertically(motionSize(Motion.GentleSize)) +
                    fadeIn(motionFloat(Motion.Standard)),
            ) {
                OptionButton(option = option, onClick = { onPick(option.taskId) })
            }
        }

        if (nextCursor != null) {
            Spacer(Modifier.height(Space.x2))
            Box(
                Modifier
                    .clip(Corner.Pill)
                    .background(colors.surfaceSunk, Corner.Pill)
                    .clickable(enabled = !loadingMore) { onShowMore(nextCursor) }
                    .padding(horizontal = Space.x4, vertical = Space.x2),
                contentAlignment = Alignment.Center,
            ) {
                if (loadingMore) {
                    CircularProgressIndicator(
                        color = colors.brandCore,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text(
                        text = "Show more",
                        style = JarvisTheme.typography.labelMedium,
                        color = colors.brandCore,
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionButton(
    option: TaskOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    // Same treatment as a task row: a done task is muted, a cancelled one is
    // struck through, a missed one is an outlined ring. One visual language
    // across the app means a status learned in the list reads the same here.
    val style = option.status?.let { statusStyle(it) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(style?.rowAlpha ?: 1f)
            .clip(Corner.Md)
            .background(colors.surface, Corner.Md)
            .border(1.dp, colors.hairline, Corner.Md)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (style != null) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .then(
                        if (style.indicatorOutlined) {
                            Modifier.border(2.dp, style.accent, CircleShape)
                        } else {
                            Modifier.background(style.accent, CircleShape)
                        },
                    ),
            )
            Spacer(Modifier.width(Space.x3))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = option.title,
                style = JarvisTheme.typography.titleMedium,
                color = style?.titleColor ?: colors.ink,
                textDecoration = style?.titleDecoration,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val whenText = option.dueAt?.let { DueDateFormat.nextFire(it) }
            if (whenText != null || style != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    // The status label sits with the date rather than in a
                    // separate chip: "Completed · Today, 9:00 am" is one fact
                    // about the task, and splitting it makes the button busier
                    // without making it clearer.
                    text = listOfNotNull(style?.label, whenText).joinToString(" · "),
                    style = JarvisTheme.typography.bodySmall.tabularNums(),
                    color = colors.inkMuted,
                )
            }
        }
        Spacer(Modifier.width(Space.x2))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}
