package com.ar13x.jarvis.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import com.ar13x.jarvis.core.model.TaskStatus

/**
 * The status treatment table from plan §6.2, in one place.
 *
 * The load-bearing requirement: `cancelled` (a decision) and `incomplete` (a
 * lapse) must stay distinguishable at a glance in both themes. That distinction
 * is the whole reason the parent plan keeps both statuses, and it is only worth
 * anything if you can see it — so it is encoded in **form as well as colour**
 * (strike-through vs outlined dot), which survives colour-blindness and dark mode.
 */
@Immutable
data class StatusStyle(
    /** Colour of the leading indicator and any status label. */
    val accent: Color,
    /** Row background wash. [Color.Transparent] for everything but `awaiting`. */
    val rowFill: Color,
    /** Title colour — muted once the task is resolved. */
    val titleColor: Color,
    /** Struck through for `cancelled`, and only for `cancelled`. */
    val titleDecoration: TextDecoration?,
    /** Outlined rather than filled indicator — the visual tell for `incomplete`. */
    val indicatorOutlined: Boolean,
    /** Whole-row alpha, dropping resolved tasks back without hiding them. */
    val rowAlpha: Float,
    val label: String,
)

@Composable
@ReadOnlyComposable
fun statusStyle(status: TaskStatus): StatusStyle {
    val c = JarvisTheme.colors
    return when (status) {
        // The default. Needs no decoration.
        TaskStatus.Active -> StatusStyle(
            accent = c.status.active,
            rowFill = Color.Transparent,
            titleColor = c.ink,
            titleDecoration = null,
            indicatorOutlined = false,
            rowAlpha = 1f,
            label = "Active",
        )
        // It wants you — the one status allowed to borrow the brand.
        TaskStatus.Awaiting -> StatusStyle(
            accent = c.status.awaitingInk,
            rowFill = c.status.awaitingFill,
            titleColor = c.ink,
            titleDecoration = null,
            indicatorOutlined = false,
            rowAlpha = 1f,
            label = "Waiting on you",
        )
        // Resolved and quiet.
        TaskStatus.Completed -> StatusStyle(
            accent = c.status.completed,
            rowFill = Color.Transparent,
            titleColor = c.inkMuted,
            titleDecoration = null,
            indicatorOutlined = false,
            rowAlpha = 0.78f,
            label = "Completed",
        )
        // A decision, calm — not an error.
        TaskStatus.Cancelled -> StatusStyle(
            accent = c.status.cancelled,
            rowFill = Color.Transparent,
            titleColor = c.inkMuted,
            titleDecoration = TextDecoration.LineThrough,
            indicatorOutlined = false,
            rowAlpha = 0.7f,
            label = "Cancelled",
        )
        // A lapse — needs attention, is not a failure. Still fully mutable.
        TaskStatus.Incomplete -> StatusStyle(
            accent = c.status.incomplete,
            rowFill = Color.Transparent,
            titleColor = c.ink,
            titleDecoration = null,
            indicatorOutlined = true,
            rowAlpha = 1f,
            label = "Missed",
        )
    }
}
