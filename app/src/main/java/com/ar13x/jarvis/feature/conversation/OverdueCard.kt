package com.ar13x.jarvis.feature.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.OverdueResolution
import com.ar13x.jarvis.core.ui.DueDateFormat
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.designsystem.theme.tabularNums

// The choices come from the component, not from here — see
// AgentComponent.Overdue.offeredMinutes. Any of them costs one allowance, and a
// manual extension and an auto-extension spend from the same two.

/**
 * The agent asking whether an overdue task got done.
 *
 * This is the only card the agent raises on its own — everything else in the
 * app answers something the user said first. It gets the same treatment as a
 * confirmation card because it does the same job: it is the moment a write
 * happens, and it has to be readable at a glance by someone who has just
 * unlocked their phone.
 *
 * **The remaining count is stated, not implied.** "One more" is the difference
 * between a deadline and a suggestion, and a user who cannot see the allowance
 * draining has no reason to treat the last one differently from the first.
 *
 * Once answered it stays in the stream in its resolved state (§5.3) — scrolling
 * back should show that you were asked and what you said, not a gap.
 */
@Composable
fun OverdueCard(
    component: AgentComponent.Overdue,
    busy: Boolean,
    onComplete: () -> Unit,
    onExtend: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    val resolved = component.resolution != null

    JarvisCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.surface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(Corner.Pill)
                    // Amber, not the brand hue and not red: a lapse needs
                    // attention and is not a failure (§6.2), and the brand
                    // cannot also mean "bad" or every CTA reads as a warning.
                    .background(colors.status.incomplete.copy(alpha = 0.16f), Corner.Pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = colors.status.incomplete,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.size(Space.x3))
            Text(
                text = "Overdue",
                style = JarvisTheme.typography.labelSmall,
                color = colors.inkMuted,
            )
            Spacer(Modifier.weight(1f))
            if (resolved) ResolutionLabel(component.resolution!!)
        }

        Spacer(Modifier.height(Space.x3))

        Text(
            text = when (component.resolution) {
                OverdueResolution.Completed -> "You marked this done."
                OverdueResolution.Extended -> "Pushed back."
                OverdueResolution.Lapsed -> "This one lapsed. You can still pick it up."
                null -> "Have you done this?"
            },
            style = JarvisTheme.typography.titleLarge,
            color = colors.ink,
        )

        component.deadline?.let { deadline ->
            Spacer(Modifier.height(Space.x1))
            Text(
                text = "Was due " + DueDateFormat.nextFire(deadline).lowercase(),
                style = JarvisTheme.typography.bodyMedium.tabularNums(),
                color = colors.inkMuted,
            )
        }

        if (!resolved) {
            Spacer(Modifier.height(Space.x4))

            PrimaryAnswer(label = "Yes, it's done", busy = busy, onClick = onComplete)

            if (component.canExtend) {
                Spacer(Modifier.height(Space.x3))
                Text(
                    text = "Not yet — give me",
                    style = JarvisTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
                Spacer(Modifier.height(Space.x2))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    component.offeredMinutes.forEach { minutes ->
                        ExtendChip(
                            minutes = minutes,
                            enabled = !busy,
                            onClick = { onExtend(minutes) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.x3))
            Text(
                // Said plainly every time. Someone who cannot see the allowance
                // draining has no reason to treat the last chance differently
                // from the first.
                text = remainingLine(component.extensionsLeft),
                style = JarvisTheme.typography.bodySmall,
                color = if (component.extensionsLeft == 0) {
                    colors.status.incomplete
                } else {
                    colors.inkMuted
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun remainingLine(left: Int): String = when (left) {
    0 -> "No more extensions — if this isn't done, it'll be marked incomplete."
    1 -> "You can push this back one more time."
    else -> "You can push this back $left more times."
}

@Composable
private fun PrimaryAnswer(label: String, busy: Boolean, onClick: () -> Unit) {
    val colors = JarvisTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(Corner.Cta)
            .background(colors.brandCore, Corner.Cta)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = Space.x3),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = colors.onBrand,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Text(label, style = JarvisTheme.typography.labelLarge, color = colors.onBrand)
        }
    }
}

@Composable
private fun ExtendChip(
    minutes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    Box(
        modifier
            .clip(Corner.Pill)
            .background(colors.surfaceSunk, Corner.Pill)
            .border(1.dp, colors.hairline, Corner.Pill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Space.x2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (minutes < 60) "$minutes min" else "1 hour",
            style = JarvisTheme.typography.labelMedium.tabularNums(),
            color = colors.ink,
        )
    }
}

@Composable
private fun ResolutionLabel(resolution: OverdueResolution) {
    val colors = JarvisTheme.colors
    val (label, tint) = when (resolution) {
        OverdueResolution.Completed -> "Done" to colors.status.completed
        OverdueResolution.Extended -> "Pushed back" to colors.inkMuted
        OverdueResolution.Lapsed -> "Incomplete" to colors.status.incomplete
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (resolution == OverdueResolution.Completed) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(Space.x1))
        }
        Text(label, style = JarvisTheme.typography.labelSmall, color = tint)
    }
}
