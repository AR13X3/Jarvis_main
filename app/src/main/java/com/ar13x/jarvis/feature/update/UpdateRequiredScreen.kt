package com.ar13x.jarvis.feature.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.ar13x.jarvis.core.update.UpdateStatus
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.component.brandWash
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.designsystem.theme.tabularNums

/**
 * Too old to talk to the gateway (plan §10.3).
 *
 * **Blocking is the correct behaviour here, and it is the only place in the app
 * that blocks.** A client sending a request shape the gateway has retired does
 * not fail cleanly — it fails as a scatter of confusing errors in unrelated
 * screens. One wall that explains itself is kinder than ten mysteries.
 *
 * The wall is only ever raised from a **live** `/health` read (see
 * `UpdateRepository`), never a remembered one. "Update to continue" while the
 * real problem is that Tailscale is off would be the worst possible message,
 * because it sends someone to fix the one thing that is not broken.
 */
@Composable
fun UpdateRequiredScreen(
    status: UpdateStatus.Blocked,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .brandWash(),
        contentAlignment = Alignment.Center,
    ) {
        JarvisCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.Gutter),
            contentPadding = Space.x6,
        ) {
            Text(
                text = "Time to update",
                style = JarvisTheme.typography.headlineMedium,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.x3))
            Text(
                text = "This version of Jarvis is too old to talk to the server. " +
                    "Update to continue.",
                style = JarvisTheme.typography.bodyMedium,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.x4))
            Text(
                // Both numbers, because "too old" without saying how old is the
                // kind of message that generates a question instead of an action.
                text = "You have " + status.installed + " · the server needs " +
                    status.minSupported + " or newer",
                style = JarvisTheme.typography.bodySmall.tabularNums(),
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Space.x6))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .clip(Corner.Cta)
                        .background(colors.brandCore, Corner.Cta)
                        .clickable { context.openUpdateChannel(status.update?.releaseUrl) }
                        .padding(horizontal = Space.x6, vertical = Space.x3),
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
}
