package com.ar13x.jarvis.reminders.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.ar13x.jarvis.core.data.AgentRepository
import com.ar13x.jarvis.core.data.message
import com.ar13x.jarvis.core.data.toFailureReason
import com.ar13x.jarvis.core.model.FailureReason
import com.ar13x.jarvis.reminders.OccurrenceMirror
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Answering the agent from the lock screen.
 *
 * Without this the follow-up loop is unanswerable at any hour: the nudge exists
 * only as a card inside a task session, nothing surfaces it, and the deadline
 * walks its two auto-extensions and lapses to `incomplete` while the user is
 * sitting right there. Being awake does not help if nothing asks.
 *
 * **A tap here writes to the gateway**, which looks like it contradicts §3.4 —
 * nothing is written without a proposal. It does not. That rule is about
 * *AI-initiated* mutations, and §5.4 draws the line explicitly: direct
 * manipulation of a cheap reversible thing does not confirm. This is the user
 * answering a question they were asked, which is as direct as it gets. The
 * proposal path is untouched: a card in a session still confirms.
 *
 * **No offline queue** (§3.5). If the gateway is unreachable the tap fails and
 * says so. Queueing a "done" to replay later against an agent that may have
 * moved the task on is precisely the problem that rule exists to avoid — and
 * here it is worse than usual, because the server is *also* running a timer
 * against the same occurrence.
 */
@AndroidEntryPoint
class NudgeActionReceiver : BroadcastReceiver() {

    @Inject lateinit var agent: AgentRepository
    @Inject lateinit var mirror: OccurrenceMirror
    @Inject lateinit var poller: NudgeCatchUp

    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getLongExtra(EXTRA_OCCURRENCE_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (occurrenceId < 0) return

        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        val action = intent.action ?: return

        // Dismiss immediately. The request takes a moment and a notification
        // that sits there after being answered reads as a tap that missed.
        if (notificationId >= 0) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = runCatching {
                    when (action) {
                        ACTION_COMPLETE -> agent.completeOccurrence(occurrenceId)
                        ACTION_EXTEND -> agent.extendOccurrence(occurrenceId, minutes)
                        else -> return@runCatching
                    }
                }

                result
                    .onSuccess {
                        // The deadline moved, so the mirror and its alarms are
                        // now wrong. Refreshing is what stops the next alarm
                        // firing against a time that no longer exists.
                        mirror.refresh()
                        poller.cancel(occurrenceId)
                    }
                    .onFailure { error ->
                        withContext(Dispatchers.Main) {
                            context.explain(error.toFailureReason())
                        }
                        // Nothing was queued. The server's own timer is still
                        // running, so the honest thing is to leave the loop
                        // alone and let the next nudge arrive.
                    }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * A toast, not a re-posted notification.
     *
     * The failure is almost always "off the tailnet", it is immediately
     * retryable, and replacing the reminder with an error would destroy the
     * thing the user was trying to answer.
     */
    private fun Context.explain(reason: FailureReason) {
        Toast.makeText(this, reason.message(), Toast.LENGTH_LONG).show()
    }

    companion object {
        const val ACTION_COMPLETE = "com.ar13x.jarvis.NUDGE_COMPLETE"
        const val ACTION_EXTEND = "com.ar13x.jarvis.NUDGE_EXTEND"
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_MINUTES = "minutes"
    }
}
