package com.ar13x.jarvis

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ar13x.jarvis.reminders.notification.Notifier
import com.ar13x.jarvis.reminders.work.OccurrenceRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class JarvisApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: Notifier

    /**
     * WorkManager's automatic initialiser is removed in the manifest so this
     * runs instead — the refresh worker takes constructor dependencies and
     * cannot be built by the default factory.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Creating the channel is idempotent and has to happen before the first
        // notification, which may come from a broadcast receiver rather than
        // from anyone opening the app.
        notifier.ensureChannel()
        OccurrenceRefreshWorker.enqueueDaily(this)
    }
}
