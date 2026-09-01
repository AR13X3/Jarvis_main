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
import com.ar13x.jarvis.core.model.SlotAlarm
import com.ar13x.jarvis.reminders.data.OccurrenceEntity
import com.ar13x.jarvis.reminders.routine.RoutineAlarmScheduler
import com.ar13x.jarvis.reminders.routine.SlotStartReceiver
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
            // Per task, not per occurrence, and deliberately: every firing of a
            // recurring reminder should open the same task, and the extras are
            // identical, so sharing one PendingIntent is correct rather than a
            // collision. `alarmKey` only to avoid `toInt()` truncating an id.
            AlarmScheduler.alarmKey(occurrence.taskId),
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

    /**
     * One action button, addressed so that it cannot be confused with another.
     *
     * **The discriminator is the data URI, not the request code** — the same
     * lesson `AlarmScheduler` already records: `PendingIntent` equality compares
     * the intent by `filterEquals`, which looks at action, data and component
     * and ignores extras entirely. Two buttons that compare equal collapse into
     * one, and `FLAG_UPDATE_CURRENT` silently rewrites the survivor's extras —
     * so "Done" on tonight's reminder would complete a different occurrence.
     *
     * The arithmetic this replaces tried to carve request codes into eight-wide
     * slots per notification. It had two faults. `notificationId * 8` overflows
     * `Int` — the id is a 31-bit hash, so `2147483647 * 8` is `-8`, not a slot.
     * And the offset added within a slot reaches 67 (`0..7` for the action plus
     * up to 60 for the minutes), overrunning the eight-wide slot into the next
     * eight occurrences' space. Both are unlikely to bite, and both fail
     * silently as the wrong task being marked done from a lock screen.
     *
     * With the URI carrying the identity, the request code only has to be
     * stable, so it is the same key the alarm uses.
     */
    private fun nudgeIntent(
        action: String,
        occurrence: OccurrenceEntity,
        notificationId: Int,
        minutes: Int = 0,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        AlarmScheduler.alarmKey(occurrence.occurrenceId),
        Intent(context, NudgeActionReceiver::class.java).apply {
            this.action = action
            data = android.net.Uri.parse(nudgeKey(occurrence.occurrenceId, action, minutes))
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

    // --- routine slots (§9.6) -----------------------------------------------

    /**
     * A separate channel from reminders, and that is the important part.
     *
     * It means Joy can silence or downgrade routine prompts in system settings
     * **without touching reminders**, which is the escape hatch that stops this
     * feature being switched off wholesale. §9.6's own warning is that too many
     * interruptions get the lot disabled; a channel is how the user disagrees
     * with a judgement call at a finer grain than "uninstall".
     *
     * `IMPORTANCE_DEFAULT`, not `HIGH`. A reminder is a moment Joy explicitly
     * asked to be interrupted at, so it heads-up and lights the screen. A slot
     * boundary is a *prompt* to log something — there are about 3.7 a day — and
     * a heads-up banner that often would be the thing that makes it intolerable.
     */
    fun ensureSlotChannel() {
        val channel = NotificationChannel(
            SLOT_CHANNEL_ID,
            "Routine",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "When a tracked part of your day begins"
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /**
     * A tracked slot has begun. One tap records the start.
     *
     * **No full-screen intent, deliberately**, where a reminder has one. Taking
     * over the lock screen is right for a deadline somebody chose; doing it
     * several times a day for "gym starts now" is how an app gets muted.
     */
    fun showSlot(alarm: SlotAlarm) {
        if (!canPost()) return
        ensureSlotChannel()

        val id = RoutineAlarmScheduler.notificationId(alarm.on, alarm.slotId)

        val start = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, SlotStartReceiver::class.java).apply {
                action = SlotStartReceiver.ACTION_START
                // Identity in the data URI (BUILD_NOTES §14). The action button
                // and the alarm share a key but target different components, so
                // they cannot collapse into one another.
                data = android.net.Uri.parse(
                    RoutineAlarmScheduler.key(alarm.on, alarm.slotId) + "/start",
                )
                putExtra(RoutineAlarmScheduler.EXTRA_ON, alarm.on.toString())
                putExtra(RoutineAlarmScheduler.EXTRA_SLOT_ID, alarm.slotId)
                putExtra(RoutineAlarmScheduler.EXTRA_LABEL, alarm.label)
                putExtra(RoutineAlarmScheduler.EXTRA_AT, alarm.at.toString())
                putExtra(RoutineAlarmScheduler.EXTRA_ENDS_AT, alarm.endsAt.toString())
                putExtra(RoutineAlarmScheduler.EXTRA_DAY_ENDS_AT, alarm.dayEndsAt.toString())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, SLOT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alarm.label)
            .setContentText(alarm.window())
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            // Not "Done". Starting is the only thing the routine records — the
            // next slot's start is what ends this one (v2 §4.4) — and a button
            // promising to finish something would be promising a write that has
            // no route behind it.
            .addAction(0, "Start", start)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** "08:00 – 09:30", the plan this slot is being measured against. */
    private fun SlotAlarm.window(): String =
        SLOT_TIME.format(at) + " – " + SLOT_TIME.format(endsAt)

    companion object {
        const val CHANNEL_ID = "jarvis.reminders"
        const val SLOT_CHANNEL_ID = "jarvis.routine"
        const val EXTRA_TASK_ID = "task_id"

        private val SLOT_TIME: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault())
    }
}

/**
 * The identity of one nudge button: which occurrence, which action, and for an
 * extension, how long.
 *
 * A plain string rather than a `Uri`, and a top-level function, so the property
 * that matters — that no two buttons which should differ ever collide — is
 * testable without a device. `Uri.parse` is a stub in unit tests.
 */
fun nudgeKey(occurrenceId: Long, action: String, minutes: Int): String =
    "jarvis://nudge/" + occurrenceId + "/" + action + "/" + minutes
