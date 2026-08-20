package com.ar13x.jarvis.reminders

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space

/**
 * What still stands between the app and a reminder that actually fires.
 *
 * Each of these fails silently if ignored — the app keeps working, tasks keep
 * being created, and nothing arrives at the right time. That is why this is
 * surfaced as a card rather than left to a settings screen nobody opens.
 */
data class ReminderReadiness(
    val canNotify: Boolean,
    val canScheduleExact: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val ready: Boolean get() = canNotify && canScheduleExact
    /** Battery is advisory: reminders work without it, just less reliably. */
    val allGood: Boolean get() = ready && batteryUnrestricted
}

@Composable
fun rememberReminderReadiness(): ReminderReadiness {
    val context = LocalContext.current
    var readiness by remember { mutableStateOf(context.readReminderReadiness()) }

    // Re-read on resume: every one of these is changed in Settings, which means
    // leaving the app and coming back is exactly the moment it changes.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        readiness = context.readReminderReadiness()
    }
    return readiness
}

private fun Context.readReminderReadiness(): ReminderReadiness {
    val alarms = getSystemService(AlarmManager::class.java)
    val power = getSystemService(PowerManager::class.java)
    return ReminderReadiness(
        canNotify = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED,
        // Revocable, so it is checked rather than assumed (plan §7.3).
        canScheduleExact = alarms.canScheduleExactAlarms(),
        batteryUnrestricted = power.isIgnoringBatteryOptimizations(packageName),
    )
}

/**
 * Asks in context (plan §5.5) — this appears in the task list only when
 * something is actually missing, and disappears the moment it is fixed. One
 * thing at a time, in the order that matters: a reminder with no notification
 * permission cannot be seen at all, so that comes before precision, which comes
 * before Samsung's battery manager.
 */
@Composable
fun ReminderSetupCard(
    readiness: ReminderReadiness,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = JarvisTheme.colors

    val notificationRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* readiness re-reads on resume */ }

    if (readiness.allGood) return

    val (headline, detail, action) = when {
        !readiness.canNotify -> Triple(
            "Turn on reminders",
            "Jarvis needs permission to notify you, or reminders will fire silently into nothing.",
            { notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )
        !readiness.canScheduleExact -> Triple(
            "Allow exact timing",
            "Without this, Android may delay a reminder by minutes or hours.",
            {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:" + context.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
        else -> Triple(
            "Stop Samsung pausing Jarvis",
            "Battery management can defer the daily refresh for days. Exempting Jarvis keeps reminders arriving.",
            {
                // Deliberately the settings screen, not the direct-request
                // dialog: this is the advisory one, and dropping someone into a
                // system list they chose to visit is friendlier than a prompt.
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }

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
                    Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = colors.brandCore,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.size(Space.x3))
            Text(headline, style = JarvisTheme.typography.titleMedium, color = colors.ink)
        }

        Spacer(Modifier.height(Space.x2))
        Text(detail, style = JarvisTheme.typography.bodyMedium, color = colors.inkMuted)
        Spacer(Modifier.height(Space.x3))

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .clip(Corner.Pill)
                    .background(colors.brandCore, Corner.Pill)
                    .clickable(onClick = action)
                    .padding(horizontal = Space.x4, vertical = Space.x2),
            ) {
                Text(
                    text = if (readiness.canNotify && readiness.canScheduleExact) "Open settings" else "Allow",
                    style = JarvisTheme.typography.labelLarge,
                    color = colors.onBrand,
                )
            }
        }
    }
}
