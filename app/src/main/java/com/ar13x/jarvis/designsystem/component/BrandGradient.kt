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
import androidx.compose.ui.graphics.ImageBitmap
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
 * ### Why the wash fades to *transparent*, not to the ground colour
 *
 * The obvious implementation interpolates brand → ground. It looks wrong, and
 * predictably so: interpolating between two opaque colours moves hue, chroma and
 * lightness together, and the eye reads the fastest-changing stretch of that
 * path as an edge. The result is two blocks meeting at a seam rather than one
 * continuous wash.
 *
 * Fading brand → transparent over the ground instead varies only *alpha*. There
 * is no second colour to travel toward, so there is no seam to find, and the
 * wash blends into whatever is beneath it — which also means the same code is
 * correct in both themes without a special case.
 *
 * ### Why so many stops
 *
 * A two-stop alpha ramp is linear, and linear alpha reads as top-heavy: the wash
 * appears to hold its strength and then give up all at once. The stops below
 * approximate an ease-out curve, which is what makes the falloff read as smooth.
 *
 * ### Banding
 *
 * Large Android gradients band visibly — stepped stripes across the wash, worst
 * on an OLED panel in dark mode. The fix is drawing into an offscreen layer
 * *and* overlaying very low-alpha noise. Blur does not fix banding; dither does.
 */
@Composable
fun BrandBackdrop(
    modifier: Modifier = Modifier,
    washHeight: Dp = DefaultWashHeight,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().brandWash(washHeight), content = content)
}

/**
 * Tall on purpose.
 *
 * The fade needs *distance* to read as a fade. Squeeze the same ramp into 300dp
 * and the eye picks out where it ends, which is the band this is meant to avoid
 * — so screens do not override this. A screen whose content starts high simply
 * has the tail of the wash running behind its first rows, which is the intent:
 * the gradient should reach the ground somewhere *past* the first card, not
 * exactly at it.
 */
val DefaultWashHeight = 520.dp

@Composable
fun Modifier.brandWash(washHeight: Dp = DefaultWashHeight): Modifier {
    val colors = JarvisTheme.colors
    val noise = rememberNoiseBrush()
    val washPx = with(LocalDensity.current) { washHeight.toPx() }

    // Dark is not an inversion: a bright crimson wash on a dark ground vibrates,
    // so it starts weaker and gives up sooner (§6.2).
    val peak = if (colors.isDark) 0.90f else 1f
    val bloomAlpha = if (colors.isDark) 0.30f else 0.42f
    val noiseAlpha = if (colors.isDark) NOISE_ALPHA_DARK else NOISE_ALPHA

    return this
        // Composites the wash as one layer, which is half of the banding fix —
        // without it the stops are quantised per draw.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val wash = washPx.coerceAtMost(size.height)
            val brand = colors.brandCore

            // Alpha-only falloff, eased. Every stop is the same hue.
            val fade = Brush.verticalGradient(
                0.00f to brand.copy(alpha = peak),
                0.22f to brand.copy(alpha = peak * 0.94f),
                0.40f to brand.copy(alpha = peak * 0.78f),
                0.55f to brand.copy(alpha = peak * 0.55f),
                0.68f to brand.copy(alpha = peak * 0.34f),
                0.79f to brand.copy(alpha = peak * 0.18f),
                0.88f to brand.copy(alpha = peak * 0.08f),
                0.95f to brand.copy(alpha = peak * 0.02f),
                1.00f to Color.Transparent,
                startY = 0f,
                endY = wash,
            )

            // The reference's brightest point is a soft glow near the top rather
            // than a hard band across the top edge, which a vertical gradient
            // alone cannot produce.
            val bloom = Brush.radialGradient(
                0.0f to brand.copy(alpha = bloomAlpha),
                0.45f to brand.copy(alpha = bloomAlpha * 0.45f),
                0.75f to brand.copy(alpha = bloomAlpha * 0.12f),
                1.0f to Color.Transparent,
                center = Offset(size.width * 0.5f, wash * 0.04f),
                radius = maxOf(size.width * 1.05f, wash * 0.9f),
            )

            onDrawBehind {
                drawRect(colors.ground)
                drawRect(fade, size = Size(size.width, wash))
                drawRect(bloom, size = Size(size.width, wash))
                // Full height, not just the wash. Clipping the dither to the
                // gradient leaves a faint horizontal seam exactly where the
                // texture stops against flat ground.
                drawRect(noise, alpha = noiseAlpha)
            }
        }
}

/** Enough to break the banding, far too little to read as texture. */
private const val NOISE_ALPHA = 0.028f
private const val NOISE_ALPHA_DARK = 0.045f
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
private fun noiseTile(size: Int = NOISE_TILE): ImageBitmap {
    val random = Random(0x5EED)
    val pixels = IntArray(size * size) {
        val value = random.nextInt(256)
        (0xFF shl 24) or (value shl 16) or (value shl 8) or value
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}
