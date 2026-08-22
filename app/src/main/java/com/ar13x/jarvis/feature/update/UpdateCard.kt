package com.ar13x.jarvis.feature.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.core.update.AvailableUpdate
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.component.toInlineMarkdown
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space

/**
 * A newer build exists (plan §10.2, step 4).
 *
 * Worth showing even though Obtainium notifies too, because the app can say
 * something Obtainium cannot: **what changed**. That is the whole reason this
 * exists, so the release notes are the body of the card rather than a link to
 * go and find them.
 *
 * Dismissible, and dismissal is remembered per version — the next release still
 * announces itself.
 */
@Composable
fun UpdateCard(
    update: AvailableUpdate,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    val context = LocalContext.current

    JarvisCard(modifier = modifier.fillMaxWidth(), contentPadding = Space.x4) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(Corner.Pill)
                    .background(colors.brandTint, Corner.Pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = colors.brandCore,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.size(Space.x3))
            Text(
                text = "Jarvis " + update.version + " is out",
                style = JarvisTheme.typography.titleMedium,
                color = colors.ink,
            )
        }

        if (update.notes != null) {
            Spacer(Modifier.height(Space.x2))
            Text(
                // Release notes are written for the person holding the phone
                // (§10.4 step 2), so they are shown, not summarised away.
                text = update.notes.trim().toInlineMarkdown(codeColor = colors.brandCore),
                style = JarvisTheme.typography.bodyMedium,
                color = colors.inkMuted,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(Space.x3))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text("Not now", color = colors.inkMuted)
            }
            Spacer(Modifier.size(Space.x2))
            Box(
                Modifier
                    .clip(Corner.Pill)
                    .background(colors.brandCore, Corner.Pill)
                    .clickable { context.openUpdateChannel(update.releaseUrl) }
                    .padding(horizontal = Space.x4, vertical = Space.x2),
            ) {
                Text(
                    text = "Open Obtainium",
                    style = JarvisTheme.typography.labelLarge,
                    color = colors.onBrand,
                )
            }
        }
    }
}
