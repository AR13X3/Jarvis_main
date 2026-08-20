package com.ar13x.jarvis.feature.conversation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.core.model.TaskStatus
import com.ar13x.jarvis.designsystem.component.softShadow
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.motion.motionColor
import com.ar13x.jarvis.designsystem.motion.motionFloat
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space

/**
 * The composer floats above the ground on `Surface` with `lg` radius and a soft
 * shadow — **never** a full-width bar pinned edge-to-edge with a top divider
 * (plan §6.4). That single choice is most of the difference between this and a
 * stock messaging app.
 *
 * One row, not a field with a control row beneath it. Empty, it should look
 * like one line of text, because that is all it is — reserving a whole second
 * row for a single button made an empty composer twice the height it earned.
 * Phase G's attachment control joins this row on the leading side (§9), so
 * there is still somewhere to grow.
 */
@Composable
fun Composer(
    text: String,
    canSend: Boolean,
    placeholder: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x3)
            .softShadow(Corner.Lg, tight = 2.dp, wide = 20.dp, tint = colors.shadowTint)
            .clip(Corner.Lg)
            .background(colors.surface, Corner.Lg)
            .then(if (colors.isDark) Modifier.border(1.dp, colors.hairline, Corner.Lg) else Modifier)
            .padding(start = Space.x4, end = Space.x2, top = Space.x2, bottom = Space.x2),
        // Bottom-aligned so the button stays beside the last line as the field
        // grows, rather than drifting to the middle of a four-line message.
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = LocalTextStyle.current.merge(
                JarvisTheme.typography.bodyLarge.copy(color = colors.ink),
            ),
            cursorBrush = SolidColor(colors.brandCore),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            modifier = Modifier
                .weight(1f)
                // Height follows the text. A single line should look like a
                // single line — the composer only earns more room once there is
                // more to show.
                .heightIn(min = 40.dp, max = 132.dp)
                .padding(vertical = Space.x2),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = JarvisTheme.typography.bodyLarge,
                            color = colors.inkMuted,
                        )
                    }
                    field()
                }
            },
        )

        Spacer(Modifier.width(Space.x2))
        // Inline rather than on a row of its own. Phase G's attachment control
        // joins this row on the leading side (§9), so the composer still has
        // somewhere to grow without being tall while empty.
        SendButton(enabled = canSend, onClick = onSend)
    }
}

@Composable
private fun SendButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors

    // The button is the only saturated thing in the composer, so it stating
    // clearly whether it will do anything matters more than it looking lively.
    val container by animateColorAsState(
        targetValue = if (enabled) colors.brandCore else colors.surfaceSunk,
        animationSpec = motionColor(Motion.SnappyColor),
        label = "sendContainer",
    )
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.92f,
        animationSpec = motionFloat(Motion.Snappy),
        label = "sendScale",
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(container, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.Send,
            contentDescription = "Send",
            tint = if (enabled) colors.onBrand else colors.inkMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Terminal tasks are read-only, and the composer is **replaced** by this rather
 * than disabled with no reason given (plan §5.3).
 *
 * The distinction matters: a greyed-out field looks broken, while a sentence
 * saying the task is finished and that questions still work is a correct
 * description of what the server will actually do — it offers `get_task` and no
 * mutation tools (parent plan §2.3).
 */
@Composable
fun ReadOnlyStrip(
    status: TaskStatus,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x3)
            .clip(Corner.Lg)
            .background(colors.surfaceSunk, Corner.Lg)
            .padding(horizontal = Space.x4, vertical = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = when (status) {
                TaskStatus.Completed -> "This task is completed — you can still ask about it."
                TaskStatus.Cancelled -> "This task was cancelled — you can still ask about it."
                else -> "This task is read-only."
            },
            style = JarvisTheme.typography.bodyMedium,
            color = colors.inkMuted,
        )
    }
}
