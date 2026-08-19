package com.ar13x.jarvis.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.ar13x.jarvis.designsystem.motion.LocalReducedMotion
import com.ar13x.jarvis.designsystem.motion.rememberReducedMotion

/**
 * **No dynamic color** (plan §6.1). Monet derives the scheme from the user's
 * wallpaper, which is the opposite of a committed identity — you cannot own a
 * brand gradient and simultaneously let the OS repaint it. Material 3 is kept
 * for component behaviour, touch targets, spacing rhythm and accessibility; its
 * palette is not.
 */
@Composable
fun JarvisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkJarvisColors else LightJarvisColors

    CompositionLocalProvider(
        LocalJarvisColors provides colors,
        LocalReducedMotion provides rememberReducedMotion(),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = JarvisTypography,
            shapes = JarvisShapes,
            content = content,
        )
    }
}

/** `JarvisTheme.colors` at a call site, mirroring `MaterialTheme.colorScheme`. */
object JarvisTheme {
    val colors: JarvisColors
        @Composable @ReadOnlyComposable get() = LocalJarvisColors.current

    val typography: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography
}

/**
 * Material components still need a scheme, so the semantic roles are projected
 * onto one. Anything Material would paint from `surfaceVariant` or a tonal
 * container is mapped deliberately rather than left to a generated tonal palette.
 */
private fun JarvisColors.toMaterialScheme(): androidx.compose.material3.ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = brandCore,
        onPrimary = onBrand,
        primaryContainer = brandTint,
        onPrimaryContainer = if (isDark) ink else brandDeep,
        secondary = brandDeep,
        onSecondary = onBrand,
        secondaryContainer = brandTint,
        onSecondaryContainer = if (isDark) ink else brandDeep,
        tertiary = brandCore,
        onTertiary = onBrand,
        background = ground,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceSunk,
        onSurfaceVariant = inkMuted,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceSunk,
        surfaceContainerHighest = surfaceSunk,
        outline = hairline,
        outlineVariant = hairline,
        scrim = scrim,
        // Material's `error` role must not be the brand hue — see §6.2's trap.
        error = status.incomplete,
        onError = onBrand,
        errorContainer = surfaceSunk,
        onErrorContainer = status.incomplete,
    )
}
