package com.ar13x.jarvis.reminders.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ar13x.jarvis.MainActivity
import com.ar13x.jarvis.R
import com.ar13x.jarvis.reminders.alarm.AlarmScheduler
import com.ar13x.jarvis.reminders.data.OccurrenceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminders arrive as notifications, fired from on-device exact alarms (§1).
 */
@Singleton
class Notifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reminders",
            // HIGH so a reminder can heads-up. A reminder that arrives silently
            // in the shade has failed at the only thing it exists to do.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Task reminders at the time you asked for them"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun canPost(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * True on API 34+ only when the user has allowed it. A reminder app is
     * exactly the category the permission exists for, but it is still revocable
     * and the notification must degrade to an ordinary heads-up without it.
     */
    fun canUseFullScreen(): Boolean =
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun show(occurrence: OccurrenceEntity) {
        if (!canPost()) return
        ensureChannel()

        val open = PendingIntent.getActivity(
            context,
            occurrence.taskId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TASK_ID, occurrence.taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificationId =
            AlarmScheduler.notificationId(occurrence.taskId, occurrence.scheduledForMillis)

        val fullScreen = PendingIntent.getActivity(
            context,
            notificationId,
            ReminderActivity.intent(
                context = context,
                title = occurrence.title,
                whenMillis = occurrence.scheduledForMillis,
                taskId = occurrence.taskId,
                notificationId = notificationId,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(occurrence.title)
            .setContentText("Due now")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            // An ordinary notification never lights a dark screen, so a reminder
            // nobody happens to look at did not happen. `true` means it takes
            // over even when the screen is on, which is the right call for a
            // moment the user explicitly asked to be interrupted at.
            //
            // Android decides: with the permission it launches the activity;
            // without it, this degrades to a heads-up and the contentIntent
            // still works. Nothing here has to branch on that.
            .setFullScreenIntent(fullScreen, true)
            .build()

        // The dedup id (plan §7.2): a push and a local alarm for the same
        // occurrence collapse into one notification rather than two.
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_ID = "jarvis.reminders"
        const val EXTRA_TASK_ID = "task_id"
    }
}
