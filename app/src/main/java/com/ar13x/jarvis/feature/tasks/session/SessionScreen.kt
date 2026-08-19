package com.ar13x.jarvis.feature.tasks.session

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.designsystem.component.BrandBackdrop
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.component.ScreenHeader
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space

/**
 * Phase A scaffold for a task session (plan §5.3) and for the unbound session
 * the `+` opens (§5.2).
 *
 * Phase C fills this in: message list, optimistic send, the pending state, the
 * inline confirmation card, and the shared-element transition from the row.
 */
@Composable
fun SessionScreen(
    taskId: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors

    BrandBackdrop(modifier = modifier.fillMaxSize(), washHeight = 280.dp) {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(
                eyebrow = if (taskId == null) "New task" else "Task " + taskId,
                headline = if (taskId == null) "What should\nI set up?" else "Call the\ndentist",
                centred = true,
                leading = {
                    CircleIconButton(onClick = onBack, diameter = 40.dp) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.ink,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )

            Spacer(Modifier.height(Space.x8))

            JarvisCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.Gutter),
            ) {
                Text(
                    text = "Session UI arrives in phase C",
                    style = JarvisTheme.typography.titleMedium,
                    color = colors.ink,
                )
                Spacer(Modifier.height(Space.x2))
                Text(
                    text = "The message list, the composer, the confirmation card " +
                        "and the row-to-session shared element are all built here.",
                    style = JarvisTheme.typography.bodyMedium,
                    color = colors.inkMuted,
                )
            }
        }
    }
}
