package com.ar13x.jarvis.designsystem.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Motion tokens (plan §6.5).
 *
 * Framer Motion has no Android port; what transfers is the *approach* — spring
 * physics by default, orchestration, and interruptible gestures. Compose does
 * all three. The discipline is defining tokens once instead of scattering
 * `tween(300)` across the codebase, so springs live here and nowhere else.
 *
 * `spring<T>` is per-type, hence the typed variants: animating an [IntOffset]
 * with a `spring<Float>` will not compile, and a visibility threshold that is
 * wrong for the type makes an animation settle visibly late.
 */
object Motion {

    // Damping / stiffness pairs — the single source of the app's feel.
    const val SnappyDamping = 0.80f
    const val SnappyStiffness = 620f

    const val StandardDamping = 0.85f
    const val StandardStiffness = 380f

    const val GentleDamping = 0.90f
    const val GentleStiffness = 190f

    /** Arrival with a little overshoot. Used sparingly — see §6.5's four places. */
    const val ExpressiveDamping = 0.62f
    const val ExpressiveStiffness = 300f

    // --- Float (alpha, scale, progress) --------------------------------------
    val Snappy: FiniteAnimationSpec<Float> = spring(SnappyDamping, SnappyStiffness)
    val Standard: FiniteAnimationSpec<Float> = spring(StandardDamping, StandardStiffness)
    val Gentle: FiniteAnimationSpec<Float> = spring(GentleDamping, GentleStiffness)
    val Expressive: FiniteAnimationSpec<Float> = spring(ExpressiveDamping, ExpressiveStiffness)

    // --- IntOffset (slide, enter/exit) ---------------------------------------
    val SnappyOffset: FiniteAnimationSpec<IntOffset> =
        spring(SnappyDamping, SnappyStiffness, IntOffset.VisibilityThreshold)
    val StandardOffset: FiniteAnimationSpec<IntOffset> =
        spring(StandardDamping, StandardStiffness, IntOffset.VisibilityThreshold)
    val GentleOffset: FiniteAnimationSpec<IntOffset> =
        spring(GentleDamping, GentleStiffness, IntOffset.VisibilityThreshold)
    val ExpressiveOffset: FiniteAnimationSpec<IntOffset> =
        spring(ExpressiveDamping, ExpressiveStiffness, IntOffset.VisibilityThreshold)

    // --- IntSize (expand/shrink, list item bounds) ---------------------------
    val StandardSize: FiniteAnimationSpec<IntSize> =
        spring(StandardDamping, StandardStiffness, IntSize.VisibilityThreshold)
    val GentleSize: FiniteAnimationSpec<IntSize> =
        spring(GentleDamping, GentleStiffness, IntSize.VisibilityThreshold)

    // --- Color ----------------------------------------------------------------
    val StandardColor: FiniteAnimationSpec<Color> = spring(StandardDamping, StandardStiffness)
    val SnappyColor: FiniteAnimationSpec<Color> = spring(SnappyDamping, SnappyStiffness)

    // --- Dp / Offset ----------------------------------------------------------
    val StandardDp: FiniteAnimationSpec<Dp> =
        spring(StandardDamping, StandardStiffness, Dp.VisibilityThreshold)
    val StandardPoint: FiniteAnimationSpec<Offset> =
        spring(StandardDamping, StandardStiffness, Offset.VisibilityThreshold)

    /** Bounds morphs — the spec behind the task row -> session shared element. */
    val StandardRect: FiniteAnimationSpec<Rect> =
        spring(StandardDamping, StandardStiffness, Rect.VisibilityThreshold)
    val ReducedRect: FiniteAnimationSpec<Rect> = tween(ReducedMillis)

    /**
     * The collapse target when the user has turned animations off. Not zero —
     * an instant swap reads as a glitch — but short enough to be imperceptible.
     */
    const val ReducedMillis = 90

    val ReducedFloat: FiniteAnimationSpec<Float> = tween(ReducedMillis)
    val ReducedOffset: FiniteAnimationSpec<IntOffset> = tween(ReducedMillis)
    val ReducedSize: FiniteAnimationSpec<IntSize> = tween(ReducedMillis)
    val ReducedColor: FiniteAnimationSpec<Color> = tween(ReducedMillis)

    /** Stagger step for orchestrated entrances. Compose's `staggerChildren`. */
    const val StaggerStepMillis = 34
}

/**
 * Honours `Settings.Global.ANIMATOR_DURATION_SCALE` (plan §6.5): when the user
 * has reduced animations we collapse to a cross-fade rather than ignoring the
 * setting. Call these instead of touching [Motion] directly in feature code.
 */
@Composable
@ReadOnlyComposable
fun motionFloat(spec: FiniteAnimationSpec<Float>): FiniteAnimationSpec<Float> =
    if (LocalReducedMotion.current) Motion.ReducedFloat else spec

@Composable
@ReadOnlyComposable
fun motionOffset(spec: FiniteAnimationSpec<IntOffset>): FiniteAnimationSpec<IntOffset> =
    if (LocalReducedMotion.current) Motion.ReducedOffset else spec

@Composable
@ReadOnlyComposable
fun motionSize(spec: FiniteAnimationSpec<IntSize>): FiniteAnimationSpec<IntSize> =
    if (LocalReducedMotion.current) Motion.ReducedSize else spec

@Composable
@ReadOnlyComposable
fun motionColor(spec: FiniteAnimationSpec<Color>): FiniteAnimationSpec<Color> =
    if (LocalReducedMotion.current) Motion.ReducedColor else spec
