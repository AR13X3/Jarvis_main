package com.ar13x.jarvis.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space

/**
 * Soft, brand-tinted elevation (plan §6.4).
 *
 * Compose's default shadows are hard and grey, and untinted grey shadow is the
 * single fastest way to make this look generic. Two passes — one tight and
 * low-alpha for contact, one wide and very-low for lift — pulled toward the
 * brand hue. `clip = false` on both so the shadow is not cut off by its own
 * shape before the surface paints.
 */
fun Modifier.softShadow(
    shape: Shape,
    tight: Dp = 2.dp,
    wide: Dp = 18.dp,
    tint: Color,
): Modifier = this
    .shadow(wide, shape, clip = false, ambientColor = tint.copy(alpha = 0.10f), spotColor = tint.copy(alpha = 0.12f))
    .shadow(tight, shape, clip = false, ambientColor = tint.copy(alpha = 0.20f), spotColor = tint.copy(alpha = 0.22f))

/**
 * A card floating on the ground.
 *
 * Rows are cards on `Surface` separated by spacing, **not** full-bleed list
 * items with divider hairlines (plan §6.7) — that difference is most of why the
 * reference reads as designed and a stock Material list does not.
 */
@Composable
fun JarvisCard(
    modifier: Modifier = Modifier,
    shape: Shape = Corner.Md,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Space.x4,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = JarvisTheme.colors
    val base = modifier
        .softShadow(shape, tint = colors.shadowTint)
        .clip(shape)
        .background(colors.surface, shape)

    val clickable = if (onClick == null) base else base.clickable(onClick = onClick)

    Column(
        modifier = clickable.padding(contentPadding),
        content = content,
    )
}

/**
 * The circular ghost button from the reference — a light sunk circle with a
 * muted icon, used for the header affordances and the composer's controls.
 */
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 40.dp,
    background: Color = JarvisTheme.colors.surfaceSunk,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(background, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = JarvisTheme.colors.brandCore),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
