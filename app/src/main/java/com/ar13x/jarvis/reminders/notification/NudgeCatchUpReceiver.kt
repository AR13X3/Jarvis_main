package com.ar13x.jarvis.reminders.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ar13x.jarvis.reminders.OccurrenceMirror
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Looks again after an unanswered nudge, and re-arms from what it finds.
 *
 * Deliberately thin: [OccurrenceMirror.refresh] already replaces the window and
 * reconciles alarms, so the whole job is "ask the server what happened". If the
 * gateway is unreachable it returns null, nothing changes, and the alarms
 * already armed stay armed — which is the right failure, because the last thing
 * the server said is still the best thing we know.
 */
@AndroidEntryPoint
class NudgeCatchUpReceiver : BroadcastReceiver() {

    @Inject lateinit var mirror: OccurrenceMirror

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NudgeCatchUp.ACTION_CATCH_UP) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mirror.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
