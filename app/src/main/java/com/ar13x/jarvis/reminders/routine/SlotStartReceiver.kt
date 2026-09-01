package com.ar13x.jarvis.reminders.routine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.ar13x.jarvis.core.data.RoutineRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * "Start", answered from the notification (§9.6).
 *
 * The same one tap the day view uses, so the routine can be driven from the lock
 * screen exactly as reminders already are — which is the whole point, since a
 * slot boundary you have to open the app to acknowledge is one you will not.
 */
@AndroidEntryPoint
class SlotStartReceiver : BroadcastReceiver() {

    @Inject lateinit var routine: RoutineRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_START) return
        val alarm = intent.toSlotAlarm() ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // THE ROUTINE IS LOADED FIRST, AND THIS IS NOT BELT AND BRACES.
                // `RemoteRoutineRepository.start` calls `requireVersion()`, whose
                // own comment reasons that "the UI cannot reach a tickable slot
                // without having rendered a routine, so this should be
                // unreachable". A notification action breaks that premise
                // exactly: this receiver can wake a cold process that has never
                // rendered anything. Collecting the flow triggers its
                // `onStart { refreshRoutine() }` and populates the version.
                routine.routine().first()

                val now = LocalDateTime.now()
                routine.start(
                    slotId = alarm.slotId,
                    // The day recorded against is the one the alarm was armed
                    // for, never derived from the clock. A Speedway slot begun
                    // at 00:05 belongs to Friday, and that is the routine's rule
                    // rather than the calendar's.
                    on = alarm.on,
                    at = now,
                    // `clamped` marks a start that landed after its own logical
                    // day had already ended — the overrun signal §4.4 keeps
                    // rather than smooths away. Computed from the day's declared
                    // end, which travelled in the extras, so it needs neither the
                    // routine object nor a second network read.
                    clamped = now.isAfter(alarm.dayEndsAt),
                )

                NotificationManagerCompat.from(context).cancel(
                    RoutineAlarmScheduler.notificationId(alarm.on, alarm.slotId),
                )
            } catch (e: Exception) {
                // Left on screen on purpose. A notification that vanished on a
                // failed write would look exactly like a successful start, and
                // the slot would silently never be recorded. Tapping again
                // retries, and the server replaces rather than duplicates.
                android.util.Log.w("SlotStartReceiver", "start failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_START = "com.ar13x.jarvis.ROUTINE_SLOT_START"
    }
}
