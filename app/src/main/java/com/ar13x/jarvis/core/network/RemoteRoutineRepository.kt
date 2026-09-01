package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.data.RoutineRepository
import com.ar13x.jarvis.core.data.SlotStart
import com.ar13x.jarvis.core.model.Routine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The routine against the gateway (`GET /routine`, `/routine/starts`).
 *
 * **This one holds state, and every other repository in the app does not.** The
 * reason is §4.2: the routine tab has to render off the tailnet, so the routine
 * is a fetch-once-and-keep object rather than a per-view read. What is cached is
 * the *last server answer*, never a local edit — there is still no write queue
 * and a failed write still fails (plan §3.5).
 *
 * The version is cached with it, and that is not incidental. Recording a start
 * needs `version_id`, the day boundary is declared on the version, and a start
 * pinned to a day but not a version silently re-buckets the next time a
 * boundary moves. So a start cannot be recorded before a routine has been read,
 * and this refuses rather than inventing one.
 */
@Singleton
class RemoteRoutineRepository @Inject constructor(
    private val api: JarvisApi,
) : RoutineRepository {

    private val cached = MutableStateFlow<Routine?>(null)
    private val log = MutableStateFlow<List<SlotStart>>(emptyList())

    /**
     * The device's zone, read at the point of use rather than captured once.
     *
     * It is the user's zone by definition — the same clock the planned times are
     * declared against and the same one `LocalDateTime.now()` reads in the day
     * view. Capturing it in a field would freeze it across a flight, which is
     * the one time it actually changes.
     */
    private val zone: ZoneId get() = ZoneId.systemDefault()

    override fun routine(): Flow<Routine> = cached
        .onStart {
            // A FAILED REFRESH MUST NOT TAKE AWAY A ROUTINE WE ALREADY HAVE.
            //
            // §4.2 requires the routine tab to render off the tailnet, and
            // `cached` is what it renders from — but `onStart` throwing cancels
            // the flow with that exception, so before this the tab broke the
            // moment the gateway was unreachable, cache or no cache. The
            // KDoc above already claimed otherwise.
            //
            // §9.6 made it matter more: the slot alarms re-arm through this
            // flow, and they re-arm from a broadcast receiver on a phone that
            // has been idle — which is exactly when a network call is least
            // likely to succeed and a stale routine is most obviously better
            // than none.
            //
            // With nothing cached there is nothing to fall back to, so the
            // error stands. That is the honest answer rather than an empty
            // screen with no explanation.
            if (cached.value == null) refreshRoutine() else runCatching { refreshRoutine() }
        }
        .filterNotNull()

    /**
     * **Deliberately NOT given the same tolerance as [routine].**
     *
     * A stale routine is still a true statement about the plan. A stale start
     * log is a claim about what was done today, and its empty state reads as
     * "nothing started" — which, shown after a failed refresh, is a lie rather
     * than staleness. Failing loudly is the right answer for this one.
     */
    override fun starts(): Flow<List<SlotStart>> = log
        .onStart { refreshStarts() }

    private suspend fun refreshRoutine() {
        val routine = gatewayCall { api.routine() }.toDomain()
        cached.value = routine
    }

    /**
     * The current routine week, which is not the calendar week.
     *
     * Both dates are required by the route on purpose: a default would have to
     * choose between "this routine week" and "the last seven days", and those
     * differ by which week Sunday belongs to. The app states which it means.
     */
    private suspend fun refreshStarts() {
        val today = LocalDate.now(zone)
        val from = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val response = gatewayCall {
            api.routineStarts(dateFrom = from.toString(), dateTo = from.plusDays(6).toString())
        }
        log.value = response.starts.map { it.toDomain(zone) }
    }

    override suspend fun start(
        slotId: String,
        on: LocalDate,
        at: LocalDateTime,
        clamped: Boolean,
    ) {
        val version = requireVersion()
        val saved = gatewayCall(mutating = true) {
            api.recordStart(
                RecordStartBody(
                    versionId = version,
                    slotKey = slotId,
                    // The day recorded against is the one on screen. It is never
                    // derived from the instant below — a Speedway shift begun at
                    // 00:05 on Saturday belongs to Friday, and that is the
                    // routine's rule, not the calendar's.
                    on = on,
                    startedAt = at.atZone(zone).toInstant(),
                    clamped = clamped,
                ),
            )
        }.toDomain(zone)

        // The server's echo, not the value just sent. If it normalised anything
        // — and it owns this row — the screen should show what was stored.
        log.value = log.value.filterNot { it.slotId == slotId && it.on == on } + saved
    }

    override suspend fun clearStart(slotId: String, on: LocalDate) {
        val version = requireVersion()
        gatewayCall(mutating = true) {
            api.clearStart(versionId = version, slotKey = slotId, on = on.toString())
        }
        log.value = log.value.filterNot { it.slotId == slotId && it.on == on }
    }

    /**
     * Refuses rather than guessing.
     *
     * A start needs the version its day boundary was declared on. There is no
     * safe default: sending the wrong version files the start against the wrong
     * day's rules, which is silent, permanent, and exactly what versioning
     * exists to prevent. The UI cannot reach a tickable slot without having
     * rendered a routine, so this should be unreachable — and if it is reached,
     * failing is the correct outcome.
     */
    private fun requireVersion(): Long = checkNotNull(cached.value?.versionId) {
        "No routine loaded: a start cannot be recorded without the version it was planned against"
    }
}
