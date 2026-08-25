package com.ar13x.jarvis.reminders.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ar13x.jarvis.reminders.alarm.AlarmScheduler
import com.ar13x.jarvis.reminders.data.OccurrenceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The link that keeps the loop alive without a push.
 *
 * When a nudge goes unanswered the server auto-extends and the deadline moves —
 * but the phone has no idea, because the mirror only refreshes on foreground
 * and once a day. Without this the chain breaks after the first nudge: no
 * second alarm, no third, and the task lapses in silence having asked once.
 *
 * So the app schedules its own check for shortly after the grace window
 * expires, refreshes the mirror, and re-arms from whatever the server now says.
 * Alarm → notification → unanswered → poll → new deadline → alarm.
 *
 * **The grace comes from the server** (`grace_minutes`, 09 §7). Assuming 15
 * would desynchronise the moment gw03 tuned it, and the symptom would be a poll
 * that runs before the extension exists and learns nothing — which looks
 * exactly like the feature not working.
 *
 * This is why §7.4's FCM question may answer itself: the whole loop runs on
 * local alarms, offline, with the gateway reached only when there is something
 * to say.
 */
@Singleton
class NudgeCatchUp @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** Schedules the check for [OccurrenceEntity.graceMinutes] plus a little slack. */
    fun schedule(occurrence: OccurrenceEntity) {
        val at = System.currentTimeMillis() +
            (occurrence.graceMinutes * 60_000L) +
            SLACK_MILLIS

        // Inexact on purpose. Nothing user-visible happens at this instant —
        // it only re-reads state — so it has no business competing for the
        // exact-alarm budget with the reminders themselves, and letting the OS
        // batch it is strictly better for the battery.
        alarms.set(AlarmManager.RTC_WAKEUP, at, pendingIntent(occurrence.occurrenceId))
    }

    /** The user answered, so the deadline is already known. */
    fun cancel(occurrenceId: Long) {
        alarms.cancel(pendingIntent(occurrenceId))
    }

    private fun pendingIntent(occurrenceId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        // Distinct from the reminder's own request codes, or cancelling one
        // would silently cancel the other.
        REQUEST_OFFSET + occurrenceId.toInt(),
        Intent(context, NudgeCatchUpReceiver::class.java).apply {
            action = ACTION_CATCH_UP
            putExtra(AlarmScheduler.EXTRA_OCCURRENCE_ID, occurrenceId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_CATCH_UP = "com.ar13x.jarvis.NUDGE_CATCH_UP"

        /**
         * Enough that the server's own poll — 60s at the time of writing — has
         * certainly run and applied the extension before we look.
         */
        private const val SLACK_MILLIS = 90_000L

        /** Keeps catch-up request codes out of the reminders' range. */
        private const val REQUEST_OFFSET = 1_000_000
    }
}
