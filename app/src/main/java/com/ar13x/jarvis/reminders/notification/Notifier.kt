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
            .setContentText(occurrence.dueLine())
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
            .apply { addNudgeActions(occurrence, notificationId) }
            .build()

        // The dedup id (plan §7.2): a push and a local alarm for the same
        // occurrence collapse into one notification rather than two.
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * The agent's question, answerable without opening the app.
     *
     * The reminder and the nudge are the same instant seen from two sides — the
     * server says "your deadline passed", the app already says "this is due
     * now" — so this extends the notification that already fires rather than
     * adding a second kind. Two notifications for one moment would be the
     * duplicate §7.2 spends its dedup id preventing.
     *
     * Only the first offered extension gets a button. Android shows three
     * actions at most, "Done" has to be one of them, and a lock screen is not
     * the place to choose between 15, 30 and 60 — the card in the session is,
     * and "Open" leads there.
     */
    private fun NotificationCompat.Builder.addNudgeActions(
        occurrence: OccurrenceEntity,
        notificationId: Int,
    ) {
        addAction(
            0,
            "Done",
            nudgeIntent(NudgeActionReceiver.ACTION_COMPLETE, occurrence, notificationId),
        )

        if (occurrence.canExtend) {
            val minutes = occurrence.offeredMinutes.first()
            addAction(
                0,
                if (minutes < 60) "+$minutes min" else "+1 hour",
                nudgeIntent(
                    NudgeActionReceiver.ACTION_EXTEND,
                    occurrence,
                    notificationId,
                    minutes,
                ),
            )
        }
    }

    private fun nudgeIntent(
        action: String,
        occurrence: OccurrenceEntity,
        notificationId: Int,
        minutes: Int = 0,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        // Unique per action AND per occurrence: sharing a request code would
        // make "Done" and "+15" the same PendingIntent, and the second would
        // silently reuse the first one's extras.
        (notificationId * 8) + action.hashCode().and(0x7) + minutes,
        Intent(context, NudgeActionReceiver::class.java).apply {
            this.action = action
            putExtra(NudgeActionReceiver.EXTRA_OCCURRENCE_ID, occurrence.occurrenceId)
            putExtra(NudgeActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NudgeActionReceiver.EXTRA_MINUTES, minutes)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Says how many chances are left, where the decision is actually made.
     *
     * "One more" is the difference between a deadline and a suggestion, and a
     * lock screen is where most of these will be answered — so the count cannot
     * live only on the card inside the app.
     */
    private fun OccurrenceEntity.dueLine(): String = when {
        !canExtend -> "Due now — last chance before this is marked incomplete"
        extensionsLeft == 1 -> "Due now — you can push this back once more"
        else -> "Due now"
    }

    companion object {
        const val CHANNEL_ID = "jarvis.reminders"
        const val EXTRA_TASK_ID = "task_id"
    }
}
