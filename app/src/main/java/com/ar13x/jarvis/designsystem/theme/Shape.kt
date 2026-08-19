package com.ar13x.jarvis.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radius scale (plan §6.4). The reference's generosity comes from [Lg] on
 * cards and [Pill] on chips and CTAs — **do not** compromise these down toward
 * Material's 4dp defaults, which is what makes an app read as stock.
 */
object Corner {
    val Xs = RoundedCornerShape(8.dp)
    val Sm = RoundedCornerShape(12.dp)
    val Md = RoundedCornerShape(20.dp)
    val Lg = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(percent = 50)

    /** Full-width primary CTAs in the reference are softened, not fully pill. */
    val Cta = RoundedCornerShape(16.dp)
}

/** Fed to Material so its own components inherit the scale rather than fighting it. */
internal val JarvisShapes = Shapes(
    extraSmall = Corner.Xs,
    small = Corner.Sm,
    medium = Corner.Md,
    large = Corner.Lg,
    extraLarge = Corner.Lg,
)
