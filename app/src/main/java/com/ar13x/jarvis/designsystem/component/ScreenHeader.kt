package com.ar13x.jarvis.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space

/**
 * The header shape both tabs share: circular ghost affordances on the shoulders,
 * a quiet eyebrow line, and an oversized headline underneath.
 *
 * The headline is the reference's loudest move and it costs nothing (plan §6.4),
 * so it is genuinely oversized with ~1.05 leading rather than a polite title.
 */
@Composable
fun ScreenHeader(
    headline: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    /** Replaces [eyebrow] when the line needs more than a sentence. */
    eyebrowContent: @Composable (() -> Unit)? = null,
    centred: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = JarvisTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.Gutter),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box { leading?.invoke() }
            Spacer(Modifier.weight(1f))
            Box { trailing?.invoke() }
        }

        Spacer(Modifier.height(Space.x8))

        if (eyebrowContent != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (centred) Arrangement.Center else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                eyebrowContent()
            }
            Spacer(Modifier.height(Space.x2))
        } else if (eyebrow != null) {
            Text(
                text = eyebrow,
                style = JarvisTheme.typography.titleMedium,
                color = colors.ink.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = if (centred) TextAlign.Center else TextAlign.Start,
            )
            Spacer(Modifier.height(Space.x1))
        }

        Text(
            text = headline,
            style = JarvisTheme.typography.displayMedium,
            color = colors.ink,
            modifier = Modifier.fillMaxWidth(),
            textAlign = if (centred) TextAlign.Center else TextAlign.Start,
        )
    }
}

/** Section headers: small, uppercase, letter-spaced, muted (plan §6.7). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title.uppercase(),
            style = JarvisTheme.typography.labelSmall,
            color = JarvisTheme.colors.inkMuted,
        )
        trailing?.invoke()
    }
}
