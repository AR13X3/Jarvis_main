package com.ar13x.jarvis.reminders

import com.ar13x.jarvis.core.data.TaskRepository
import com.ar13x.jarvis.reminders.alarm.AlarmScheduler
import com.ar13x.jarvis.reminders.data.OccurrenceDao
import com.ar13x.jarvis.reminders.data.OccurrenceEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetch the window, replace the mirror, reconcile the alarms (plan §7.1).
 *
 * The whole of phase E's correctness lives in the order of those three steps and
 * in the fact that the middle one *replaces* rather than merges. The server owns
 * the schedule; the device is allowed to remember exactly what the last fetch
 * said and nothing else.
 */
@Singleton
class OccurrenceMirror @Inject constructor(
    private val tasks: TaskRepository,
    private val dao: OccurrenceDao,
    private val alarms: AlarmScheduler,
) {

    /**
     * @return the number of alarms now armed, or null if the gateway could not
     *   be reached — the caller decides whether that is worth retrying.
     */
    suspend fun refresh(withinHours: Int = WINDOW_HOURS): Int? {
        val previous = dao.all()

        val window = runCatching { tasks.upcomingOccurrences(withinHours) }.getOrNull()
            // Unreachable is the common case, not an exception: the phone is off
            // the tailnet more often than not. The alarms already armed stay
            // armed — they are the last thing the server told us, and dropping
            // them because we could not ask again would turn a network blip into
            // a missed reminder.
            ?: return null

        val rows = window.occurrences.map { occurrence ->
            OccurrenceEntity(
                occurrenceId = occurrence.occurrenceId,
                taskId = occurrence.taskId,
                title = occurrence.title,
                scheduledForMillis = occurrence.scheduledFor.toEpochMilli(),
                isPriority = occurrence.isPriority,
                extensionsUsed = occurrence.extensionsUsed,
                extensionsAllowed = occurrence.extensionsAllowed,
                graceMinutes = occurrence.graceMinutes,
                extensionMinutes = occurrence.extensionMinutes.joinToString(","),
            )
        }

        dao.replaceWindow(rows)
        alarms.reconcile(previous = previous, current = rows)
        return rows.count { it.scheduledForMillis > System.currentTimeMillis() }
    }

    /**
     * Re-arms from the mirror without going to the network.
     *
     * This is the boot path (plan §7.2): alarms do not survive a reboot, and the
     * phone may come up with no connectivity at all. Re-arming from the last
     * known window is what makes a reminder survive a restart on a plane.
     */
    suspend fun rearmFromMirror() {
        val rows = dao.all()
        alarms.reconcile(previous = emptyList(), current = rows)
    }

    suspend fun occurrence(id: Long): OccurrenceEntity? = dao.byId(id)

    companion object {
        /** The ~48h window the parent plan specifies (§2.8). */
        const val WINDOW_HOURS = 48
    }
}
