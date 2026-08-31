package com.ar13x.jarvis.reminders.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ar13x.jarvis.reminders.data.OccurrenceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms exact alarms for the mirrored window (plan §7.2).
 *
 * Everything here is one of the four things the plan warns get forgotten:
 * `setExactAndAllowWhileIdle` so it survives Doze, re-arming on boot, re-arming
 * after the daily refresh, and a stable id so a push and a local alarm for the
 * same occurrence collapse into one notification.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    /**
     * `canScheduleExactAlarms()` exists from API 31, which is why `minSdk` is 31
     * (plan §2) — there is no compatibility branch here, deliberately.
     *
     * **Handle the false case.** The permission is revocable, and an app that
     * assumed it would silently stop reminding.
     */
    fun canScheduleExact(): Boolean = alarms.canScheduleExactAlarms()

    /**
     * Replaces the armed set with exactly [occurrences].
     *
     * Takes the previous set so alarms for occurrences that have gone are
     * cancelled. Arming is idempotent — a `PendingIntent` with the same id and
     * extras replaces rather than duplicates — but *cancelling* is not
     * automatic, and a stale alarm fires for a reminder the server has dropped.
     */
    fun reconcile(previous: List<OccurrenceEntity>, current: List<OccurrenceEntity>) {
        val keep = current.mapTo(mutableSetOf()) { it.occurrenceId }
        previous.filterNot { it.occurrenceId in keep }.forEach(::cancel)

        val now = System.currentTimeMillis()
        current.forEach { occurrence ->
            // A window fetched a moment ago can still contain something that has
            // just passed. Arming it would fire immediately for a reminder whose
            // moment has gone.
            if (occurrence.scheduledForMillis > now) arm(occurrence)
        }
    }

    fun cancelAll(occurrences: List<OccurrenceEntity>) = occurrences.forEach(::cancel)

    private fun arm(occurrence: OccurrenceEntity) {
        if (!canScheduleExact()) return
        alarms.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            occurrence.scheduledForMillis,
            pendingIntent(occurrence, PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }

    private fun cancel(occurrence: OccurrenceEntity) {
        alarms.cancel(pendingIntent(occurrence, PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun pendingIntent(occurrence: OccurrenceEntity, flags: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            // In the data URI, not just extras: PendingIntent equality ignores
            // extras entirely, so two occurrences differing only by extra would
            // be the same alarm and the second would overwrite the first.
            data = android.net.Uri.parse("jarvis://occurrence/" + occurrence.occurrenceId)
            putExtra(EXTRA_OCCURRENCE_ID, occurrence.occurrenceId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmKey(occurrence.occurrenceId),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE = "com.ar13x.jarvis.ALARM_FIRE"
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"

        /**
         * Dedup key: `(task_id, fire_at)` (plan §7.2).
         *
         * Derived rather than stored so a push and a local alarm for the same
         * occurrence land on the same notification id and collapse into one
         * notification instead of two. It is `(task, time)` and not
         * `occurrence_id` on purpose — the push may not know the occurrence id,
         * but it certainly knows which task and when.
         */
        /**
         * The identity of an occurrence's **alarm**, stable for its whole life.
         *
         * Deliberately not [notificationId], which was doing this job and is
         * built from the fire time. The two want opposite things and cannot be
         * one number:
         *
         * - a *notification* id must change with the time, so a push and a local
         *   alarm for the same firing collapse and a later firing is a new one;
         * - an *alarm* id must **not**, or moving a reminder cannot replace its
         *   own alarm.
         *
         * With the time in the request code, rescheduling 11:00 to 11:30 built a
         * different `PendingIntent`, so `FLAG_UPDATE_CURRENT` had nothing to
         * update and `reconcile` never cancelled the old one — it is still in
         * `current`, by the same occurrence id. Both alarms stayed armed and the
         * reminder fired twice, once at a time the user had already moved.
         *
         * `AlarmDedupTest` pins the time-varying half; [AlarmIdentityTest] pins
         * this one.
         */
        fun alarmKey(occurrenceId: Long): Int = (occurrenceId.hashCode()) and 0x7FFFFFFF

        fun notificationId(taskId: Long, fireAtMillis: Long): Int {
            var hash = taskId.hashCode()
            hash = 31 * hash + fireAtMillis.hashCode()
            // Positive: some OEM notification shades behave oddly with negative
            // ids, and the sign carries no information here.
            return hash and 0x7FFFFFFF
        }
    }
}
