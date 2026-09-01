package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.Routine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When a tracked slot was actually begun.
 *
 * There is no stop. Starting the next slot is what ends this one, so a slot's
 * real duration is the gap to the following start (v2 plan §4.4). That makes one
 * tap the whole interaction, and it means doing gym after uni records itself
 * without anyone declaring an order was broken — which is the variation the
 * feature exists to surface.
 *
 * [on] is the *logical* day this belongs to, which is not always the calendar
 * date of [at]: a Speedway shift started on Friday is still Friday's at 00:05.
 */
data class SlotStart(
    /**
     * The routine version this start was measured against.
     *
     * **Version first, and not optional.** The day boundary is declared *on* the
     * version, so a start pinned only to a logical day silently re-buckets the
     * moment a later version moves that day's wake time — rewriting exactly the
     * history versioning exists to protect. gw03 caught this: the first shape
     * carried only `(on, slotId, at)`.
     *
     * The gateway owns version identity. Until those routes exist the fake
     * supplies the routine's `effectiveFrom`, which is a stand-in and not a
     * proposed format.
     */
    val routineVersion: String,
    val on: LocalDate,
    val slotId: String,
    val at: LocalDateTime,
    /**
     * True when [at] fell in a gap between routine days and was attributed to
     * the day that just ended.
     *
     * 48 hours a week belong to no logical day. A 3am tap is a late finish, not
     * a new day — but a run of clamped starts *is* the overrun signal, so it is
     * recorded rather than smoothed away.
     */
    val clamped: Boolean = false,
)

interface RoutineRepository {
    fun routine(): Flow<Routine>

    fun starts(): Flow<List<SlotStart>>

    /** Records a start, replacing any earlier one for the same slot and day. */
    suspend fun start(slotId: String, on: LocalDate, at: LocalDateTime, clamped: Boolean = false)

    /** Undoes a start — a mis-tap should not need a whole editing surface. */
    suspend fun clearStart(slotId: String, on: LocalDate)
}

/**
 * The routine, in memory, from Joy's real week.
 *
 * The gateway owns this state and will replace it — the routine template, its
 * versions and the start log are all server-side in the v2 plan. This exists so
 * the day view can be built and judged before that contract is agreed, which is
 * how phases A–C worked and the reason they were not blocked on gw03.
 *
 * Nothing here survives process death, deliberately: a fake that persisted would
 * be mistaken for a working feature.
 */
@Singleton
class FakeRoutineRepository @Inject constructor() : RoutineRepository {

    private val log = MutableStateFlow<List<SlotStart>>(emptyList())

    override fun routine(): Flow<Routine> = flowOf(RoutineFixture.theWeek)

    override fun starts(): Flow<List<SlotStart>> = log.asStateFlow()

    override suspend fun start(slotId: String, on: LocalDate, at: LocalDateTime, clamped: Boolean) {
        val version = RoutineFixture.theWeek.effectiveFrom.toString()
        log.update { existing ->
            existing.filterNot { it.slotId == slotId && it.on == on } +
                SlotStart(version, on, slotId, at, clamped)
        }
    }

    override suspend fun clearStart(slotId: String, on: LocalDate) {
        log.update { existing -> existing.filterNot { it.slotId == slotId && it.on == on } }
    }
}
