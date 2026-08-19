package com.ar13x.jarvis.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.motion.motionColor
import com.ar13x.jarvis.designsystem.motion.motionFloat
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space

enum class JarvisTab(val label: String) {
    Tasks("Tasks"),
    Chat("Chat"),
}

val JarvisBottomBarHeight = 64.dp

/**
 * Two destinations, Tasks first (plan §5.1).
 *
 * Hand-built rather than Material's `NavigationBar` for one reason: M3's bar
 * paints a tonal surface and an indicator pill from its own colour roles, both
 * of which fight a design where the ground is warm near-white and the only
 * saturated element is meant to be the accent. The behaviour M3 is kept for —
 * touch targets, semantics — is preserved here explicitly.
 */
@Composable
fun JarvisBottomBar(
    current: JarvisTab,
    onSelect: (JarvisTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.98f))
            .navigationBarsPadding()
            .height(JarvisBottomBarHeight)
            .padding(horizontal = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        JarvisTab.entries.forEach { tab ->
            TabItem(
                tab = tab,
                selected = tab == current,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabItem(
    tab: JarvisTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    val interaction = remember { MutableInteractionSource() }

    val tint by animateColorAsState(
        targetValue = if (selected) colors.brandCore else colors.inkMuted,
        animationSpec = motionColor(Motion.StandardColor),
        label = "tabTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = motionFloat(Motion.Snappy),
        label = "tabScale",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = Space.x1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) colors.brandTint else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (tab) {
                    JarvisTab.Tasks -> if (selected) Icons.Rounded.Checklist else Icons.Outlined.ChecklistRtl
                    JarvisTab.Chat -> if (selected) Icons.Rounded.ChatBubble else Icons.Outlined.ChatBubbleOutline
                },
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(20.dp).scale(scale),
            )
        }
        Text(
            text = tab.label,
            style = JarvisTheme.typography.labelMedium,
            color = tint,
            textAlign = TextAlign.Center,
        )
    }
}
