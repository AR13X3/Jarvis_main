package com.ar13x.jarvis.reminders.routine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ar13x.jarvis.core.model.SlotAlarm
import com.ar13x.jarvis.reminders.notification.Notifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * A tracked slot has begun (§9.6).
 *
 * Everything it needs is in the extras, deliberately: the routine lives in an
 * in-memory cache that a cold receiver process does not have, and a notification
 * that needed the network to say "gym starts now" would fail exactly when the
 * phone has been sitting idle — which is every time.
 *
 * The payload is at most 48 hours old, because that is the arming window, and a
 * slot whose label changed inside that window is the failure this trades for
 * working offline. `AlarmReceiver` makes the opposite trade for reminders and
 * can, because its mirror is on disk.
 */
@AndroidEntryPoint
class SlotAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var notifier: Notifier

    @Inject lateinit var refresher: RoutineAlarmRefresher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RoutineAlarmScheduler.ACTION_SLOT) return

        val alarm = intent.toSlotAlarm() ?: return

        // A slot that has already been started needs no prompt. Not checked
        // here: knowing it needs the start log, which is network-backed and
        // absent in a cold process, and a notification that failed to appear
        // because a read timed out is worse than one that arrives redundantly.
        // Tapping Start twice is idempotent server-side — it replaces the row.
        notifier.showSlot(alarm)

        // Extend the window by the one just consumed, so the armed set does not
        // drain to nothing between daily refreshes. Held open with `goAsync`
        // because a receiver's process can be killed the moment `onReceive`
        // returns, and the notification above has already been posted.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refresher.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Rebuilds the alarm from its own extras, or `null` if anything is missing.
 *
 * **`null` means show nothing**, and that is the guard that makes imperfect
 * cancellation safe (see `RoutineAlarmScheduler`). An alarm armed by an older
 * build, or for a slot since deleted, arrives without a usable payload and is
 * dropped rather than shown half-filled.
 */
internal fun Intent.toSlotAlarm(): SlotAlarm? {
    val on = getStringExtra(RoutineAlarmScheduler.EXTRA_ON) ?: return null
    val slotId = getStringExtra(RoutineAlarmScheduler.EXTRA_SLOT_ID) ?: return null
    val label = getStringExtra(RoutineAlarmScheduler.EXTRA_LABEL) ?: return null
    val at = getStringExtra(RoutineAlarmScheduler.EXTRA_AT) ?: return null
    val endsAt = getStringExtra(RoutineAlarmScheduler.EXTRA_ENDS_AT) ?: return null
    val dayEndsAt = getStringExtra(RoutineAlarmScheduler.EXTRA_DAY_ENDS_AT) ?: return null

    return runCatching {
        SlotAlarm(
            on = LocalDate.parse(on),
            slotId = slotId,
            label = label,
            at = LocalDateTime.parse(at),
            endsAt = LocalDateTime.parse(endsAt),
            dayEndsAt = LocalDateTime.parse(dayEndsAt),
        )
    }.getOrNull()
}
