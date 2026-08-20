package com.ar13x.jarvis.reminders.notification

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.ar13x.jarvis.MainActivity
import com.ar13x.jarvis.designsystem.component.BrandBackdrop
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.designsystem.theme.tabularNums
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The full-screen reminder — what a task reminder looks like when the phone is
 * face-down on a table at 11pm.
 *
 * An ordinary notification never lights a dark screen, so a reminder you do not
 * happen to look at is a reminder that did not happen. This is the platform's
 * answer to that, and the same argument that justified `USE_EXACT_ALARM`: the
 * app's entire purpose is arriving at the right moment.
 *
 * It shows over the keyguard rather than behind it, and deliberately shows only
 * the task's title and time — tapping through to anything else requires an
 * unlock, so nothing private is reachable from a locked phone.
 */
@AndroidEntryPoint
class ReminderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Both are required and do different jobs: one gets the window past the
        // keyguard, the other actually powers the display on.
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val whenMillis = intent.getLongExtra(EXTRA_WHEN, 0L)
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        setContent {
            JarvisTheme {
                ReminderScreen(
                    title = title,
                    whenMillis = whenMillis,
                    onDismiss = { finishAndDismiss(notificationId) },
                    onOpen = {
                        // Dismissing the keyguard first means the task session is
                        // never shown to someone holding a locked phone.
                        getSystemService(KeyguardManager::class.java)
                            ?.requestDismissKeyguard(this, null)
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                .putExtra(Notifier.EXTRA_TASK_ID, taskId),
                        )
                        finishAndDismiss(notificationId)
                    },
                )
            }
        }
    }

    private fun finishAndDismiss(notificationId: Int) {
        if (notificationId >= 0) {
            NotificationManagerCompat.from(this).cancel(notificationId)
        }
        finish()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_WHEN = "when"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun intent(
            context: Context,
            title: String,
            whenMillis: Long,
            taskId: Long,
            notificationId: Int,
        ): Intent = Intent(context, ReminderActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_WHEN, whenMillis)
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
    }
}

@Composable
private fun ReminderScreen(
    title: String,
    whenMillis: Long,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = JarvisTheme.colors
    val time = remember(whenMillis) {
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            .format(Instant.ofEpochMilli(whenMillis).atZone(ZoneId.systemDefault()))
    }

    BrandBackdrop(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = Space.Gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Space.x12))

            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.brandCore, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = colors.onBrand,
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(Modifier.height(Space.x6))
            Text(
                text = time,
                style = JarvisTheme.typography.titleMedium.tabularNums(),
                color = colors.ink.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(Space.x2))
            Text(
                text = title,
                style = JarvisTheme.typography.displayMedium,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                ReminderAction(
                    label = "Dismiss",
                    filled = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                ReminderAction(
                    label = "Open",
                    filled = true,
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Space.x12))
        }
    }
}

@Composable
private fun ReminderAction(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(Corner.Cta)
            .background(if (filled) colors.brandCore else colors.surfaceSunk, Corner.Cta)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = JarvisTheme.typography.labelLarge,
            color = if (filled) colors.onBrand else colors.ink,
        )
    }
}
