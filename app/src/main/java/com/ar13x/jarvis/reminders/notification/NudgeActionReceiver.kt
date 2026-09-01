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
import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.ui.DueDateFormat
import com.ar13x.jarvis.reminders.OccurrenceMirror
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
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
                        else -> return@launch
                    }
                }

                result
                    .onSuccess { task ->
                        // The deadline moved, so the mirror and its alarms are
                        // now wrong. Refreshing is what stops the next alarm
                        // firing against a time that no longer exists.
                        mirror.refresh()
                        poller.cancel(occurrenceId)

                        // **Say that it worked.** Without this the only thing
                        // this button could ever tell you was that it had
                        // failed: the notification is dismissed the instant it
                        // is tapped, and success was silent. Joy pushed a
                        // reminder back three times because two silent
                        // successes are indistinguishable from nothing
                        // happening, and got feedback only when the third was
                        // refused for having no extensions left.
                        withContext(Dispatchers.Main) {
                            context.confirm(action, minutes, task)
                        }
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
     * What just happened, and when the reminder now expects an answer.
     *
     * The new time rather than a bare acknowledgement: the reason to push a
     * reminder back is to move it somewhere, and "pushed back" without saying
     * where leaves you exactly as uncertain as saying nothing did.
     */
    private fun Context.confirm(action: String, minutes: Int, task: Task) {
        val message = nudgeConfirmation(action, minutes, task) ?: return
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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

/**
 * What to say after a nudge action succeeds.
 *
 * A top-level function because the wording is the whole fix and `Toast` is not
 * testable off-device. The branch that matters is the length: "60 minutes" is
 * how a machine says an hour.
 */
fun nudgeConfirmation(
    action: String,
    minutes: Int,
    task: Task,
    zone: ZoneId = ZoneId.systemDefault(),
): String? = when (action) {
    NudgeActionReceiver.ACTION_COMPLETE -> "Marked done."
    NudgeActionReceiver.ACTION_EXTEND -> {
        val length = if (minutes < 60) minutes.toString() + " minutes" else "an hour"
        "Pushed back " + length + " — now " + DueDateFormat.forRow(task, zone) + "."
    }
    else -> null
}
