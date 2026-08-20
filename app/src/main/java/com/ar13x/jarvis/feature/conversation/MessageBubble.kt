package com.ar13x.jarvis.feature.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.core.model.Message
import com.ar13x.jarvis.core.model.MessageRole
import com.ar13x.jarvis.designsystem.component.softShadow
import com.ar13x.jarvis.designsystem.component.toInlineMarkdown
import com.ar13x.jarvis.designsystem.motion.LocalReducedMotion
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import androidx.compose.runtime.LaunchedEffect

/** User right, agent left (plan §5.3). */
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    val fromUser = message.role == MessageRole.User

    // Asymmetric corners: the tight one marks the side the message came from,
    // which is what lets you read the direction of a conversation at a glance
    // without needing an avatar on every row.
    val shape = if (fromUser) {
        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .then(
                    if (fromUser) {
                        Modifier.background(colors.brandCore, shape)
                    } else {
                        Modifier
                            .softShadow(shape, tight = 1.dp, wide = 12.dp, tint = colors.shadowTint)
                            .background(colors.surface, shape)
                            .then(
                                if (colors.isDark) Modifier.border(1.dp, colors.hairline, shape) else Modifier,
                            )
                    },
                )
                .clip(shape)
                .padding(horizontal = Space.x4, vertical = Space.x3),
        ) {
            // Only the agent's prose is parsed. The user's own text is
            // rendered exactly as typed — nobody writing "2 * 3 * 4" means
            // emphasis, and silently restyling what someone wrote is worse
            // than showing an asterisk.
            if (fromUser) {
                Text(
                    text = message.text,
                    style = JarvisTheme.typography.bodyLarge,
                    color = colors.onBrand,
                )
            } else {
                Text(
                    text = message.text.toInlineMarkdown(codeColor = colors.brandDeep),
                    style = JarvisTheme.typography.bodyLarge,
                    color = colors.ink,
                )
            }
        }
    }
}

/**
 * Agent bubbles enter with a little overshoot, fading and rising 8dp (plan
 * §6.5, move 2). Stagger nothing here — one bubble at a time.
 *
 * Keyed on the message so it plays once on arrival and not again when the list
 * recomposes for an unrelated reason.
 */
@Composable
fun Arrival(
    key: Any,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val rise = with(LocalDensity.current) { 8.dp.toPx() }
    val progress = remember(key) { Animatable(if (reduced) 1f else 0f) }

    LaunchedEffect(key) {
        if (!reduced) progress.animateTo(1f, Motion.Expressive)
    }

    Box(
        modifier.graphicsLayer {
            // Expressive overshoots past 1, which is the point for position and
            // wrong for opacity — an alpha above 1 is not brighter, it is a
            // clamp, so the fade would appear to finish early.
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * rise
        },
    ) {
        content()
    }
}

/**
 * The pending state.
 *
 * The plan is explicit that a ~4s median means this is seen constantly and
 * "it is not a spinner" (§5.3). So it is the agent's own bubble, in place, with
 * three dots breathing — the shape of the answer arrives before the answer does,
 * and the reply lands in the space the indicator was already holding.
 */
@Composable
fun ThinkingBubble(modifier: Modifier = Modifier) {
    val colors = JarvisTheme.colors
    val reduced = LocalReducedMotion.current
    val shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            modifier = Modifier
                .softShadow(shape, tight = 1.dp, wide = 12.dp, tint = colors.shadowTint)
                .background(colors.surface, shape)
                .then(if (colors.isDark) Modifier.border(1.dp, colors.hairline, shape) else Modifier)
                .clip(shape)
                .padding(horizontal = Space.x4, vertical = Space.x4),
            horizontalArrangement = Arrangement.spacedBy(Space.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(DOTS) { index ->
                ThinkingDot(index = index, animated = !reduced)
            }
        }
    }
}

private const val DOTS = 3
private const val DOT_CYCLE_MS = 1_100

@Composable
private fun ThinkingDot(index: Int, animated: Boolean) {
    val colors = JarvisTheme.colors
    val transition = rememberInfiniteTransition(label = "thinking")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(DOT_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            // Offsetting the start rather than the duration keeps all three dots
            // on one clock, so they never drift apart over a long wait.
            initialStartOffset = androidx.compose.animation.core.StartOffset(
                offsetMillis = index * (DOT_CYCLE_MS / DOTS),
            ),
        ),
        label = "dot" + index,
    )

    // A smooth rise and fall rather than a blink: the dot is never fully gone,
    // so the row reads as breathing instead of flashing.
    val wave = if (animated) {
        val t = phase * 2f
        if (t <= 1f) t else 2f - t
    } else {
        0.5f
    }

    Box(
        Modifier
            .size(7.dp)
            .graphicsLayer {
                val scale = 0.72f + 0.28f * wave
                scaleX = scale
                scaleY = scale
                alpha = 0.42f + 0.58f * wave
            }
            .clip(CircleShape)
            .background(colors.inkMuted, CircleShape),
    )
}
