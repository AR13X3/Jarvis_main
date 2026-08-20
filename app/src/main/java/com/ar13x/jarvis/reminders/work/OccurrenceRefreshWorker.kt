package com.ar13x.jarvis.reminders.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ar13x.jarvis.reminders.OccurrenceMirror
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Refreshes the mirrored window daily (plan §7.1).
 *
 * The daily cadence against a 48h window is deliberate slack: it means a missed
 * run does not immediately cost a reminder, which matters because the target is
 * a Samsung and Samsung defers periodic work aggressively — that deferral is the
 * whole reason §2.8's FCM measurement exists.
 */
@HiltWorker
class OccurrenceRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mirror: OccurrenceMirror,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val armed = mirror.refresh()
        // Null means the gateway was unreachable — retry rather than fail, since
        // being off the tailnet is the ordinary case and the existing alarms are
        // still armed meanwhile.
        return if (armed == null) Result.retry() else Result.success()
    }

    companion object {
        private const val DAILY = "jarvis.occurrence.refresh.daily"
        private const val NOW = "jarvis.occurrence.refresh.now"

        fun enqueueDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<OccurrenceRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DAILY,
                // KEEP, not UPDATE: replacing the request on every launch resets
                // its period, and an app opened daily would never actually run
                // the periodic work.
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Foreground refresh (plan §7.1) and the post-boot catch-up. */
        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<OccurrenceRefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
