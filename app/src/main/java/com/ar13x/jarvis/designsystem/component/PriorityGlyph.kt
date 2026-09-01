package com.ar13x.jarvis.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.core.model.TodoPriority
import com.ar13x.jarvis.designsystem.theme.JarvisTheme

/**
 * A to-do's priority, as one glyph on a row (§9.4.1).
 *
 * **`normal` draws nothing at all, and that is the design rather than an
 * omission.** Most to-dos are normal; a glyph on every row would be a column of
 * identical marks that says nothing and costs the width that the title needs. A
 * priority mark should mean "this one is not like the others", which it can only
 * do by being absent from the others.
 *
 * **The distinction is carried by form first and colour second** — a double
 * chevron, a single arrow up, nothing, an arrow down — which is the same rule
 * `StatusStyle` follows for `cancelled` versus `incomplete`, and for the same
 * reason: it survives dark mode, colour-blindness, and a 20dp icon.
 *
 * The colour is [StatusColors.incomplete] (amber) rather than the brand red.
 * The brand is the accent and is reserved for the thing you are meant to press;
 * a backlog row shouting in the same colour as the `+` would compete with it,
 * and plan §6.7 says the saturated element is the one the eye should land on.
 */
@Composable
fun PriorityGlyph(
    priority: TodoPriority,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
) {
    val icon = priority.icon ?: return
    val colors = JarvisTheme.colors

    Icon(
        imageVector = icon,
        // The only place the priority is named. Every glyph here is a shape a
        // screen reader cannot describe, and "arrow upward" is not the fact.
        contentDescription = priority.spoken,
        tint = when (priority) {
            TodoPriority.Highest, TodoPriority.High -> colors.status.incomplete
            // Low is quieter than ordinary text, because it is a de-emphasis.
            // Drawing it in the alert colour would make "do this last" shout.
            TodoPriority.Low -> colors.inkMuted
            TodoPriority.Normal -> Color.Unspecified
        },
        modifier = modifier.size(size),
    )
}

/** `null` for [TodoPriority.Normal] — see [PriorityGlyph]. */
val TodoPriority.icon: ImageVector?
    get() = when (this) {
        TodoPriority.Highest -> Icons.Rounded.KeyboardDoubleArrowUp
        TodoPriority.High -> Icons.Rounded.ArrowUpward
        TodoPriority.Normal -> null
        TodoPriority.Low -> Icons.Rounded.ArrowDownward
    }

/** What a screen reader says, and what the detail screen's chips read. */
val TodoPriority.spoken: String
    get() = when (this) {
        TodoPriority.Highest -> "Highest priority"
        TodoPriority.High -> "High priority"
        TodoPriority.Normal -> "Normal priority"
        TodoPriority.Low -> "Low priority"
    }

/** The short form, for a chip in a row of four. */
val TodoPriority.shortLabel: String
    get() = when (this) {
        TodoPriority.Highest -> "Highest"
        TodoPriority.High -> "High"
        TodoPriority.Normal -> "Normal"
        TodoPriority.Low -> "Low"
    }
