package com.ar13x.jarvis.reminders.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ar13x.jarvis.reminders.OccurrenceMirror
import com.ar13x.jarvis.reminders.notification.NudgeCatchUp
import com.ar13x.jarvis.reminders.notification.Notifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fires one reminder. */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var mirror: OccurrenceMirror
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var catchUp: NudgeCatchUp

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val occurrenceId = intent.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE_ID, -1L)
        if (occurrenceId < 0) return

        // The database read is asynchronous but a receiver's process can be
        // killed the moment onReceive returns, so the work is held open.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Read the title from the mirror rather than carrying it in the
                // intent: a task renamed since the alarm was armed should fire
                // under its current name, and the mirror is refreshed daily.
                mirror.occurrence(occurrenceId)?.let { occurrence ->
                    notifier.show(occurrence)
                    // If this goes unanswered the server extends it and the
                    // deadline moves without telling us. Looking again after the
                    // grace window is what keeps the chain going to the next
                    // alarm rather than stopping after one question.
                    if (occurrence.canExtend) catchUp.schedule(occurrence)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
