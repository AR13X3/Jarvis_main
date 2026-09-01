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
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.motion.motionColor
import com.ar13x.jarvis.designsystem.motion.motionFloat
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space

/**
 * The five destinations (§9.2). Joy asked for the dashboard and to-dos as tabs
 * by name, so neither is demoted back to a nested screen.
 *
 * [label] is no longer drawn — see [JarvisBottomBar]. It is the tab's **name**
 * and it is what a screen reader announces, so it is still load-bearing text
 * rather than a leftover.
 *
 * **"Tasks" is labelled *Reminders*, and that is the v2 plan's own word.** §9 of
 * that document lists the four surfaces as Reminders, Tasks, Routine and
 * Dashboard, where its *Reminders* is this app's `Tasks` tab and its *Tasks* is
 * the to-do screen. With both on the bar at once the old label would have had
 * two things called some form of "task" sitting next to each other. The enum
 * constant keeps its name so that routes, graphs and every call site are
 * untouched; only the word the user sees is corrected.
 */
enum class JarvisTab(val label: String) {
    Tasks("Reminders"),
    Todos("To-dos"),
    Routine("Routine"),
    Dashboard("Dashboard"),
    Chat("Chat"),
}

val JarvisBottomBarHeight = 64.dp

/**
 * Five destinations, **icon-only** (§9.2).
 *
 * Hand-built rather than Material's `NavigationBar` for one reason: M3's bar
 * paints a tonal surface and an indicator pill from its own colour roles, both
 * of which fight a design where the ground is warm near-white and the only
 * saturated element is meant to be the accent. The behaviour M3 is kept for —
 * touch targets, semantics — is preserved here explicitly.
 *
 * **Dropping the labels makes `contentDescription` the only name a screen reader
 * gets**, so every tab still carries one and it is a real word rather than a
 * glyph name. The row height is unchanged at [JarvisBottomBarHeight]: five cells
 * are already narrower than three were, and shrinking the target vertically to
 * match would make the bar harder to hit in both directions at once.
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
                    // A BELL, not a second checklist. Tasks used to be a
                    // checklist and could be, because the word "Tasks" sat
                    // underneath it and no other tab competed. With to-dos on
                    // the bar and no labels at all, two checklists side by side
                    // would be two tabs the user has to learn by position. A
                    // reminder is the thing that interrupts you; that is a bell
                    // in every app anyone has ever used.
                    JarvisTab.Tasks ->
                        if (selected) Icons.Rounded.NotificationsActive else Icons.Outlined.NotificationsNone
                    // The checklist moves here, where it was always the honest
                    // glyph: a to-do is a thing you tick off.
                    JarvisTab.Todos ->
                        if (selected) Icons.Rounded.Checklist else Icons.Outlined.ChecklistRtl
                    JarvisTab.Routine ->
                        if (selected) Icons.Rounded.Schedule else Icons.Outlined.Schedule
                    JarvisTab.Dashboard ->
                        if (selected) Icons.Rounded.Insights else Icons.Outlined.Insights
                    JarvisTab.Chat ->
                        if (selected) Icons.Rounded.ChatBubble else Icons.Outlined.ChatBubbleOutline
                },
                // The only name this tab has now. Kept deliberately, and it is a
                // word rather than a glyph name.
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(20.dp).scale(scale),
            )
        }
    }
}
