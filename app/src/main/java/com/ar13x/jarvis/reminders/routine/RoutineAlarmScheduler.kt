package com.ar13x.jarvis.reminders.routine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ar13x.jarvis.core.model.Routine
import com.ar13x.jarvis.core.model.SlotAlarm
import com.ar13x.jarvis.core.model.trackedSlotAlarms
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exact alarms for the routine's tracked slots (§9.6).
 *
 * Joy: *"I am not getting any notifications for routines, we need those as
 * well."* There was no routine notification path at all, so the day view only
 * worked if you were already looking at it — which is precisely what tracking
 * adherence cannot rely on.
 *
 * This reuses the machinery rather than rebuilding it: the same `AlarmManager`,
 * the same `setExactAndAllowWhileIdle`, the same re-arm-on-boot obligation. What
 * is new is only *what* gets armed.
 *
 * ### Identity — BUILD_NOTES §14, which this codebase has already paid for
 *
 * `PendingIntent` equality ignores extras entirely, so the identity goes in the
 * **data URI**: `jarvis://slot/<logical day>/<slot id>`. That cannot collide
 * with a reminder's `jarvis://occurrence/<id>` — different authority, different
 * URI, different intent, and a different receiver component besides.
 *
 * **The key deliberately excludes the slot's time**, which is the specific trap
 * §14 names: *"never key an alarm on anything that can change while it is
 * armed."* A routine edit that moves gym from 08:00 to 09:00 keeps the same
 * `(day, slot)` key, so re-arming *replaces* the existing alarm instead of
 * leaving both armed. Keying on the time is exactly how a reminder once fired
 * twice, once at an hour the user had already moved.
 *
 * The request code is a stable hash of that same key. Per §14 it only has to be
 * stable, never unique, and it is never arithmetic on an id — every scheme in
 * this codebase built that way has overflowed, truncated or overrun.
 *
 * ### Cancellation, and why it is allowed to be imperfect
 *
 * Re-arming replaces an alarm whose `(day, slot)` still exists, and slots that
 * fall out of the window are cancelled explicitly. The one case not covered is a
 * slot **deleted** from the routine while its alarm is armed, because computing
 * its key needs a routine that no longer contains it — and persisting the armed
 * set to answer that would be a second source of truth about a thing the routine
 * already knows.
 *
 * Instead `SlotAlarmReceiver` refuses to show a notification whose slot no longer
 * exists. A stale alarm therefore wakes the device once and does nothing, which
 * is the cheap failure; a stale alarm that *fired* would be the expensive one.
 */
@Singleton
class RoutineAlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = alarms.canScheduleExactAlarms()

    /**
     * Arms exactly the tracked slots due in the window, and cancels the rest.
     *
     * **It takes no "previously armed" list**, unlike `AlarmScheduler.reconcile`,
     * because there is nowhere honest to keep one: the occurrence mirror is a
     * Room table on disk, and the routine's only cache is an in-memory
     * `StateFlow` that a cold process does not have. What it does instead is
     * recompute a deliberately wider window — four days around now — and cancel
     * everything in it that is not being armed. Cancelling a `PendingIntent`
     * that was never armed is a no-op, so the extra breadth costs nothing.
     *
     * Returns what is now armed, or an empty list when the exact-alarm
     * permission has been revoked. That permission is revocable and an app that
     * assumed it would silently stop notifying.
     */
    fun reconcile(
        routine: Routine,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<SlotAlarm> {
        val current = routine.trackedSlotAlarms(now)
        val keep = current.mapTo(mutableSetOf()) { key(it.on, it.slotId) }

        // Everything this routine could have armed in the recent past but no
        // longer wants. Cancelling a `PendingIntent` that was never armed is a
        // no-op, so casting the net wider than necessary costs nothing.
        routine.trackedSlotAlarms(now.minusDays(2), java.time.Duration.ofDays(4))
            .filterNot { key(it.on, it.slotId) in keep }
            .forEach { cancel(it.on, it.slotId) }

        if (!canScheduleExact()) return emptyList()
        current.forEach(::arm)
        return current
    }

    fun cancelAll(routine: Routine, now: LocalDateTime = LocalDateTime.now()) {
        routine.trackedSlotAlarms(now.minusDays(2), java.time.Duration.ofDays(4))
            .forEach { cancel(it.on, it.slotId) }
    }

    private fun arm(alarm: SlotAlarm) {
        alarms.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            alarm.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            pendingIntent(alarm, PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }

    private fun cancel(on: LocalDate, slotId: String) {
        val intent = Intent(context, SlotAlarmReceiver::class.java).apply {
            action = ACTION_SLOT
            data = Uri.parse(key(on, slotId))
        }
        alarms.cancel(
            PendingIntent.getBroadcast(
                context,
                requestCode(on, slotId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    private fun pendingIntent(alarm: SlotAlarm, flags: Int): PendingIntent {
        val intent = Intent(context, SlotAlarmReceiver::class.java).apply {
            action = ACTION_SLOT
            // The identity. Extras below are payload and are never consulted
            // when two PendingIntents are compared.
            data = Uri.parse(key(alarm.on, alarm.slotId))
            putExtra(EXTRA_ON, alarm.on.toString())
            putExtra(EXTRA_SLOT_ID, alarm.slotId)
            putExtra(EXTRA_LABEL, alarm.label)
            putExtra(EXTRA_AT, alarm.at.toString())
            putExtra(EXTRA_ENDS_AT, alarm.endsAt.toString())
            putExtra(EXTRA_DAY_ENDS_AT, alarm.dayEndsAt.toString())
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(alarm.on, alarm.slotId),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_SLOT = "com.ar13x.jarvis.ROUTINE_SLOT"

        const val EXTRA_ON = "slot_on"
        const val EXTRA_SLOT_ID = "slot_id"
        const val EXTRA_LABEL = "slot_label"
        const val EXTRA_AT = "slot_at"
        const val EXTRA_ENDS_AT = "slot_ends_at"
        const val EXTRA_DAY_ENDS_AT = "slot_day_ends_at"

        /**
         * The alarm's identity, as a URI string.
         *
         * `(logical day, slot id)` and **nothing else** — see the class note.
         * The authority `slot` is what keeps it clear of `jarvis://occurrence/`.
         */
        fun key(on: LocalDate, slotId: String): String =
            "jarvis://slot/" + on + "/" + encodeSlotId(slotId)

        /**
         * Percent-encodes a slot id for use in the key.
         *
         * Written out rather than calling `Uri.encode`, and that is not
         * not-invented-here: this is the identity function BUILD_NOTES §14 says
         * keeps getting broken, and an identity function that cannot be unit
         * tested is one nobody checks. `Uri.encode` is an Android stub that
         * throws in a plain JVM test, so every existing identity test in this
         * package — all of which are plain JUnit — would have had to become a
         * Robolectric test to reach it.
         *
         * Unreserved characters per RFC 3986. Today's ids are all `fri-speedway`
         * shaped and none of this fires; it is here so that the day one arrives
         * with a slash or a space in it, the key stays one path segment.
         */
        internal fun encodeSlotId(slotId: String): String = buildString {
            for (ch in slotId) {
                if (ch.isLetterOrDigit() && ch.code < 128 || ch in "-_.~") {
                    append(ch)
                } else {
                    for (byte in ch.toString().toByteArray(Charsets.UTF_8)) {
                        append('%')
                        append("%02X".format(byte.toInt() and 0xFF))
                    }
                }
            }
        }

        /**
         * Stable, and derived from the whole key rather than from arithmetic on
         * part of it (§14, rule 3).
         */
        fun requestCode(on: LocalDate, slotId: String): Int =
            key(on, slotId).hashCode() and 0x7FFFFFFF

        /**
         * The notification id for a slot.
         *
         * Same value as the request code, and that is fine here in a way it
         * would not be for a reminder: a reminder's notification id must vary
         * with the *fire time* so a push and a local alarm for one firing
         * collapse, whereas nothing else in the system posts a routine slot.
         * One slot on one day is one notification.
         */
        fun notificationId(on: LocalDate, slotId: String): Int = requestCode(on, slotId)
    }
}
