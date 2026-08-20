package com.ar13x.jarvis.reminders.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ar13x.jarvis.reminders.OccurrenceMirror
import com.ar13x.jarvis.reminders.work.OccurrenceRefreshWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * **Alarms do not survive a reboot.** Re-arming here is, in the plan's words,
 * "the single most commonly forgotten line in this whole document" (§7.2).
 *
 * Also handles `MY_PACKAGE_REPLACED`: an app update clears alarms in exactly the
 * same way a reboot does, and the failure looks identical — reminders silently
 * stop after an Obtainium update, which is precisely when nobody is looking.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var mirror: OccurrenceMirror

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Unit
            else -> return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // From the mirror, not the network: the phone may boot with no
                // connectivity, and the last known window is better than nothing.
                mirror.rearmFromMirror()
                // Then ask the server, in case the window moved while it was off.
                OccurrenceRefreshWorker.enqueueNow(context)
                OccurrenceRefreshWorker.enqueueDaily(context)
            } finally {
                pending.finish()
            }
        }
    }
}
