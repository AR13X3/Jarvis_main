package com.ar13x.jarvis.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour roles. Feature code reads these, never [Palette] directly.
 *
 * This exists instead of leaning on Material's [androidx.compose.material3.ColorScheme]
 * because the app needs roles M3 has no name for — `ground` vs `surface` vs
 * `surfaceSunk`, a brand tint, a hairline, and a status scale that deliberately
 * contains no red (plan §6.2).
 */
@Immutable
data class JarvisColors(
    val brandCore: Color,
    val brandDeep: Color,
    val brandTint: Color,
    val onBrand: Color,
    val ground: Color,
    val surface: Color,
    val surfaceSunk: Color,
    val ink: Color,
    val inkMuted: Color,
    val hairline: Color,
    /** Shadows are tinted toward the brand hue — untinted grey reads generic (§6.4). */
    val shadowTint: Color,
    val scrim: Color,
    val status: StatusColors,
    val isDark: Boolean,
)

@Immutable
data class StatusColors(
    /** `active` is the default and needs no decoration. */
    val active: Color,
    /** `awaiting` wants you — the one status allowed to borrow the brand. */
    val awaitingFill: Color,
    val awaitingInk: Color,
    /** Resolved and quiet. */
    val completed: Color,
    /** A *decision*, calm — not an error. */
    val cancelled: Color,
    /** A *lapse* — needs attention, is not a failure. */
    val incomplete: Color,
)

internal val LightJarvisColors = JarvisColors(
    brandCore = BrandCore,
    brandDeep = BrandDeep,
    brandTint = BrandTint,
    onBrand = Color.White,
    ground = Ground,
    surface = Surface,
    surfaceSunk = SurfaceSunk,
    ink = Ink,
    inkMuted = InkMuted,
    hairline = Hairline,
    shadowTint = Color(0xFF4A1F27),
    scrim = Color(0x99150F10),
    status = StatusColors(
        active = InkMuted,
        awaitingFill = BrandTint,
        awaitingInk = BrandDeep,
        completed = StatusGreen,
        cancelled = StatusGrey,
        incomplete = StatusAmber,
    ),
    isDark = false,
)

internal val DarkJarvisColors = JarvisColors(
    brandCore = BrandCoreDark,
    brandDeep = BrandDeepDark,
    brandTint = BrandTintDark,
    onBrand = Color.White,
    ground = GroundDark,
    surface = SurfaceDark,
    surfaceSunk = SurfaceSunkDark,
    ink = InkDark,
    inkMuted = InkMutedDark,
    hairline = HairlineDark,
    shadowTint = Color(0xFF000000),
    scrim = Color(0xB3000000),
    status = StatusColors(
        active = InkMutedDark,
        awaitingFill = BrandTintDark,
        awaitingInk = Color(0xFFFF9FAC),
        completed = StatusGreenDark,
        cancelled = StatusGreyDark,
        incomplete = StatusAmberDark,
    ),
    isDark = true,
)

internal val LocalJarvisColors = staticCompositionLocalOf<JarvisColors> {
    error("JarvisColors not provided — wrap the content in JarvisTheme { }.")
}
