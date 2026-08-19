package com.ar13x.jarvis.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.motion.motionColor
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space

/**
 * Filter chip: pill, [SurfaceSunk] inactive, [BrandTint] fill with brand text
 * active (plan §6.7).
 *
 * Hand-built rather than M3's `FilterChip`, which insists on an outline, a
 * leading check icon and its own container roles — three things that would each
 * have to be fought back one at a time.
 */
@Composable
fun JarvisChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = JarvisTheme.colors
    val interaction = remember { MutableInteractionSource() }

    val container by animateColorAsState(
        targetValue = if (selected) colors.brandTint else colors.surfaceSunk,
        animationSpec = motionColor(Motion.SnappyColor),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            if (colors.isDark) colors.ink else colors.brandDeep
        } else {
            colors.inkMuted
        },
        animationSpec = motionColor(Motion.SnappyColor),
        label = "chipContent",
    )

    Row(
        modifier = modifier
            .clip(Corner.Pill)
            .background(container, Corner.Pill)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.brandCore),
                onClick = onClick,
            )
            .padding(horizontal = Space.x3, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        }
        Text(text = label, style = JarvisTheme.typography.labelMedium, color = content)
    }
}
