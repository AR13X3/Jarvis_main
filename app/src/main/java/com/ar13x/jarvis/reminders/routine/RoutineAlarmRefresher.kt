package com.ar13x.jarvis.reminders.routine

import com.ar13x.jarvis.core.data.RoutineRepository
import com.ar13x.jarvis.core.model.SlotAlarm
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-arms the routine's slot alarms (§9.6).
 *
 * One place, called from the three moments alarms are lost or go stale:
 *
 * - **boot and app update**, where Android clears every alarm. The plan calls
 *   re-arming here "the single most commonly forgotten line in this whole
 *   document" (§7.2), and a routine that silently stopped notifying after an
 *   Obtainium update would look exactly like a routine nobody had set up.
 * - **the daily refresh**, which rolls the 48-hour window forward.
 * - **each slot firing**, so the window extends by one as it is consumed rather
 *   than draining between daily runs.
 *
 * Failure is swallowed and reported as `false`. Every caller is a receiver or a
 * worker whose real job is something else, and none of them can do anything
 * useful about a routine that would not load — the previously armed alarms are
 * still armed, which is the right thing to fall back to.
 */
@Singleton
class RoutineAlarmRefresher @Inject constructor(
    private val routine: RoutineRepository,
    private val scheduler: RoutineAlarmScheduler,
) {

    /** The alarms now armed, or `null` if the routine could not be read. */
    suspend fun refresh(): List<SlotAlarm>? = runCatching {
        // `first()` triggers the repository's own `onStart { refreshRoutine() }`,
        // so this both loads and returns it. In a cold process there is nothing
        // cached and this is the fetch; in a warm one it is the cache.
        scheduler.reconcile(routine.routine().first())
    }.getOrNull()
}
