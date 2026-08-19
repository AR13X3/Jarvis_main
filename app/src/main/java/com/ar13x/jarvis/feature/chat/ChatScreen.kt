package com.ar13x.jarvis.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
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
 * Phase A scaffold for the Chat tab (plan §5.4).
 *
 * Phase C adds the general session and the lookup flow — ask, get asked for a
 * date, then pick from buttons three at a time.
 */
@Composable
fun ChatScreen(
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors

    BrandBackdrop(modifier = modifier.fillMaxSize(), washHeight = 340.dp) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(
                eyebrow = "Hi there,",
                headline = "What would you like\nto sort out today?",
                centred = true,
                leading = {
                    CircleIconButton(onClick = {}, diameter = 40.dp) {
                        Icon(
                            Icons.AutoMirrored.Rounded.MenuOpen,
                            contentDescription = "Sessions",
                            tint = colors.ink,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                trailing = {
                    CircleIconButton(onClick = {}, diameter = 40.dp) {
                        Icon(
                            Icons.Rounded.HistoryToggleOff,
                            contentDescription = "History",
                            tint = colors.ink,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )

            Spacer(Modifier.height(Space.x8))

            JarvisCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.Gutter),
                onClick = { onOpenTask(1L) },
            ) {
                Text(
                    text = "Talk with Jarvis",
                    style = JarvisTheme.typography.titleMedium,
                    color = colors.ink,
                )
                Spacer(Modifier.height(Space.x2))
                Text(
                    text = "Ask about a task in plain language. If more than one " +
                        "matches, Jarvis asks for the date and offers them as buttons.",
                    style = JarvisTheme.typography.bodyMedium,
                    color = colors.inkMuted,
                )
            }
        }
    }
}
