package com.ar13x.jarvis.designsystem.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import kotlin.random.Random

/**
 * The hero gradient (plan §6.3) — the app's signature, behind the Chat header,
 * onboarding, and the Tasks header in a shorter form.
 *
 * Two things beyond a plain vertical gradient:
 *
 * 1. A **radial bloom** near the top. The reference's brightest point is a soft
 *    glow rather than a hard band across the top edge, and a pure
 *    `verticalGradient` cannot produce that.
 * 2. **Noise dither.** Large Android gradients band visibly — stepped stripes
 *    across the wash, worst on an OLED panel in dark mode. The fix is drawing
 *    into an offscreen layer *and* overlaying very low-alpha noise. Blur does
 *    not fix banding; dither does.
 *
 * Done once here so it is never hand-rolled again.
 */
@Composable
fun BrandBackdrop(
    modifier: Modifier = Modifier,
    washHeight: Dp = 340.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().brandWash(washHeight), content = content)
}

@Composable
fun Modifier.brandWash(washHeight: Dp = 340.dp): Modifier {
    val colors = JarvisTheme.colors
    val noise = rememberNoiseBrush()
    val washPx = with(LocalDensity.current) { washHeight.toPx() }

    // Dark is not an inversion: a bright crimson wash on a dark ground vibrates,
    // so the bloom is weaker and the gradient reaches the ground sooner (§6.2).
    val bloomAlpha = if (colors.isDark) 0.38f else 0.55f
    val midAlpha = if (colors.isDark) 0.28f else 0.35f

    return this
        // Forces the wash to composite as one layer, which is half of the
        // banding fix — without it the stops are quantised per-draw.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val wash = washPx.coerceAtMost(size.height)

            val vertical = Brush.verticalGradient(
                0f to colors.brandCore,
                0.55f to colors.brandCore.copy(alpha = midAlpha),
                1f to colors.ground,
                startY = 0f,
                endY = wash,
            )
            val bloom = Brush.radialGradient(
                0f to colors.brandCore.copy(alpha = bloomAlpha),
                0.6f to colors.brandDeep.copy(alpha = bloomAlpha * 0.35f),
                1f to Color.Transparent,
                center = Offset(size.width * 0.5f, wash * 0.06f),
                radius = maxOf(size.width * 0.95f, wash * 0.80f),
            )

            onDrawBehind {
                drawRect(colors.ground)
                drawRect(vertical, size = Size(size.width, wash))
                drawRect(bloom, size = Size(size.width, wash))
                drawRect(noise, size = Size(size.width, wash), alpha = NOISE_ALPHA)
            }
        }
}

/** Enough to break the banding, far too little to read as texture. */
private const val NOISE_ALPHA = 0.028f
private const val NOISE_TILE = 128

@Composable
private fun rememberNoiseBrush(): ShaderBrush = remember {
    ShaderBrush(ImageShader(noiseTile(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * Generated rather than shipped as a PNG: a tiling noise bitmap is a few lines
 * of arithmetic, and an asset would have to be authored per density to avoid
 * being resampled into smooth — which is the one thing it must not be.
 *
 * The seed is fixed so the texture is identical every launch.
 */
private fun noiseTile(size: Int = NOISE_TILE): androidx.compose.ui.graphics.ImageBitmap {
    val random = Random(0x5EED)
    val pixels = IntArray(size * size) {
        val value = random.nextInt(256)
        (0xFF shl 24) or (value shl 16) or (value shl 8) or value
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}
