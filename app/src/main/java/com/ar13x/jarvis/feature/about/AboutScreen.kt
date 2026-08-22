package com.ar13x.jarvis.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.clip
import com.ar13x.jarvis.BuildConfig
import com.ar13x.jarvis.core.update.UpdateStatus
import com.ar13x.jarvis.feature.update.UpdateViewModel
import com.ar13x.jarvis.feature.update.openUpdateChannel
import androidx.compose.ui.platform.LocalContext
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.component.ScreenHeader
import com.ar13x.jarvis.designsystem.component.brandWash
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.designsystem.theme.tabularNums
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Build identity, one tap away (plan §10.1).
 *
 * The plan's reason for this screen is worth restating, because it is the whole
 * design brief: *when something misbehaves on the phone, the first question is
 * always "which build is that?"*. So everything here is a fact about **this
 * APK** — nothing is fetched, nothing can fail, and the screen renders
 * identically with Tailscale off.
 *
 * Every value comes from [BuildConfig], which is generated at compile time from
 * `app/build.gradle.kts`. That is what makes them trustworthy: `versionCode` is
 * derived from `versionName` in the build script rather than typed twice, so the
 * two cannot disagree here or anywhere else.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    updates: UpdateViewModel = hiltViewModel(),
) {
    val colors = JarvisTheme.colors
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .brandWash()
            .verticalScroll(scroll),
    ) {
        ScreenHeader(
            eyebrow = "About",
            headline = "Jarvis",
            centred = false,
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

        Column(
            modifier = Modifier.padding(horizontal = Space.Gutter),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            VersionCard()
            UpdateCard(status = updates.status.collectAsStateWithLifecycle().value)
            GatewayCard()
        }

        Spacer(Modifier.height(Space.x12))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * The version, given the weight it actually carries.
 *
 * `versionName` is the number a person says out loud and `versionCode` is the
 * one Obtainium compares, so both are shown together in the plan's exact
 * `1.4.0 (10400)` shape rather than the name alone.
 */
@Composable
private fun VersionCard() {
    val colors = JarvisTheme.colors

    JarvisCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = BuildConfig.VERSION_NAME,
                style = JarvisTheme.typography.displayMedium.tabularNums(),
                color = colors.ink,
            )
            Spacer(Modifier.width(Space.x2))
            Text(
                text = "(" + BuildConfig.VERSION_CODE + ")",
                style = JarvisTheme.typography.titleMedium.tabularNums(),
                color = colors.inkMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Spacer(Modifier.height(Space.x4))

        // The git SHA is the only one of these that identifies the *source*
        // rather than the release, which is what makes it the useful one when a
        // version was built more than once.
        AboutRow(label = "Commit", value = BuildConfig.GIT_SHA)
        AboutRow(label = "Built", value = buildTime())
        AboutRow(label = "Variant", value = BuildConfig.BUILD_TYPE)
    }
}

/**
 * The badge half of §10.2, step 4 — the banner is the other half.
 *
 * A version number with nothing next to it cannot answer "am I behind?", which
 * is the second question anyone asks after "which build is this?". So the
 * answer sits directly under it.
 */
@Composable
private fun UpdateCard(status: UpdateStatus) {
    val colors = JarvisTheme.colors
    val context = LocalContext.current
    val available = (status as? UpdateStatus.Available)?.update

    JarvisCard(onClick = if (available == null) null else ({ context.openUpdateChannel(available.releaseUrl) })) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (available == null) colors.status.completed else colors.brandCore, CircleShape),
            )
            Spacer(Modifier.width(Space.x3))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (available == null) "Up to date" else "Version " + available.version + " is available",
                    style = JarvisTheme.typography.titleMedium,
                    color = colors.ink,
                )
                Text(
                    text = if (available == null) {
                        "Checked daily against the releases repo."
                    } else {
                        "Tap to open Obtainium."
                    },
                    style = JarvisTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun GatewayCard() {
    JarvisCard {
        Text(
            text = "Gateway",
            style = JarvisTheme.typography.labelSmall,
            color = JarvisTheme.colors.inkMuted,
        )
        Spacer(Modifier.height(Space.x2))
        Text(
            text = BuildConfig.DEFAULT_GATEWAY_URL,
            style = JarvisTheme.typography.bodyMedium,
            color = JarvisTheme.colors.ink,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Label left, value right, values in tabular numerals so they align. */
@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.x1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = JarvisTheme.typography.bodyMedium,
            color = JarvisTheme.colors.inkMuted,
        )
        Spacer(Modifier.width(Space.x4))
        Text(
            text = value,
            style = JarvisTheme.typography.bodyMedium.tabularNums(),
            color = JarvisTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val buildTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())

/**
 * `BUILD_TIME` is an ISO-8601 instant stamped by the build script, rendered in
 * the device's zone — the wall clock the person holding the phone recognises.
 *
 * Unparseable falls back to the raw string rather than throwing: a diagnostic
 * screen that crashes while reporting a diagnostic is the worst possible
 * outcome, and a raw timestamp is still an answer.
 */
private fun buildTime(): String = runCatching {
    buildTimeFormat.format(Instant.parse(BuildConfig.BUILD_TIME).atZone(ZoneId.systemDefault()))
}.getOrDefault(BuildConfig.BUILD_TIME)

