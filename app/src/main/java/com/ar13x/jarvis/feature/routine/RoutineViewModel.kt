package com.ar13x.jarvis.feature.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.core.data.RoutineRepository
import com.ar13x.jarvis.core.data.SlotStart
import com.ar13x.jarvis.core.model.LogicalDay
import com.ar13x.jarvis.core.model.Routine
import com.ar13x.jarvis.core.model.RoutineSlot
import com.ar13x.jarvis.core.model.SlotKind
import com.ar13x.jarvis.core.model.logicalDayAt
import com.ar13x.jarvis.core.model.nextDayAfter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** Where a slot sits relative to now. Drives the order things appear in. */
enum class SlotPosition { Past, Current, Upcoming }

/**
 * One slot, placed on a real date and against the start log.
 *
 * [actualEnd] is null while a slot is still open — nothing has been started
 * after it and the day has not finished.
 */
data class SlotRow(
    val slot: RoutineSlot,
    val plannedStart: LocalDateTime,
    val plannedEnd: LocalDateTime,
    val actualStart: LocalDateTime?,
    val actualEnd: LocalDateTime?,
    val position: SlotPosition,
) {
    val started: Boolean get() = actualStart != null

    /**
     * Started somewhere other than planned, by more than a few minutes.
     *
     * The tolerance exists because nobody taps at the second, and a routine that
     * called a four-minute difference a deviation would cry wolf until it was
     * ignored.
     */
    val drifted: Boolean
        get() = actualStart != null &&
            Math.abs(java.time.Duration.between(plannedStart, actualStart).toMinutes()) > 15

    /**
     * A tracked slot whose time has passed with nothing recorded.
     *
     * Not the same as failed. It cannot tell "I skipped gym" from "I did gym and
     * forgot to tap", which is why §4.4 wants a prompt at day end rather than a
     * silent verdict.
     */
    val unrecorded: Boolean
        get() = slot.kind == SlotKind.Tracked && actualStart == null && position == SlotPosition.Past
}

data class DayView(
    val logical: LogicalDay,
    val isToday: Boolean,
    val rows: List<SlotRow>,
) {
    val past: List<SlotRow> get() = rows.filter { it.position == SlotPosition.Past }
    val current: SlotRow? get() = rows.firstOrNull { it.position == SlotPosition.Current }
    val upcoming: List<SlotRow> get() = rows.filter { it.position == SlotPosition.Upcoming }

    val trackedTotal: Int get() = rows.count { it.slot.kind == SlotKind.Tracked }
    val startedCount: Int get() = rows.count { it.slot.kind == SlotKind.Tracked && it.started }
    val unrecordedCount: Int get() = rows.count { it.unrecorded }
}

data class RoutineUiState(
    val routine: Routine? = null,
    val selected: DayOfWeek = DayOfWeek.MONDAY,
    val day: DayView? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    /** True when no routine day is running — between sleeping and waking. */
    val resting: Boolean = false,
    val nextDayStartsAt: LocalDateTime? = null,
)

/**
 * The routine day view (v2 plan §4.9).
 *
 * Everything time-shaped is delegated to `RoutineClock`. Nothing here derives a
 * calendar day from a timestamp — the boundary between one routine day and the
 * next is declared by the routine and is frequently not midnight.
 */
@HiltViewModel
class RoutineViewModel @Inject constructor(
    private val repository: RoutineRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RoutineUiState())
    val state: StateFlow<RoutineUiState> = _state.asStateFlow()

    private val now = MutableStateFlow(LocalDateTime.now())

    /**
     * The day the user chose, and the routine day that was running when they
     * chose it.
     *
     * Both, because a choice should outlast a scroll but not outlast the day.
     * Sticking to it forever means browsing to Friday on a Tuesday leaves the
     * tab on Friday tomorrow, and the tab exists to answer "what now" — so the
     * pick is dropped as soon as a different day starts running.
     */
    private var picked: Pair<DayOfWeek, LocalDate?>? = null

    init {
        combine(repository.routine(), repository.starts(), now) { routine, starts, at ->
            recompute(routine, starts, at)
        }.launchIn(viewModelScope)

        // A minute is the finest granularity anything here shows, so anything
        // faster would be recomposition for its own sake.
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                now.value = LocalDateTime.now()
            }
        }
    }

    fun select(weekday: DayOfWeek) {
        val routine = _state.value.routine
        picked = weekday to routine?.logicalDayAt(now.value)?.date
        _state.value = _state.value.copy(selected = weekday)
        routine?.let { recompute(it, lastStarts, now.value) }
    }

    fun start(row: SlotRow) {
        val day = _state.value.day ?: return
        viewModelScope.launch {
            if (row.started) {
                repository.clearStart(row.slot.id, day.logical.date)
            } else {
                repository.start(row.slot.id, day.logical.date, LocalDateTime.now())
            }
        }
    }

    private var lastStarts: List<SlotStart> = emptyList()

    private fun recompute(routine: Routine, starts: List<SlotStart>, at: LocalDateTime) {
        lastStarts = starts

        val running = routine.logicalDayAt(at)
        val next = if (running == null) routine.nextDayAfter(at) else null

        // Until the user picks a day, follow whatever is actually running —
        // opening the tab at 00:30 on a Saturday should land on Friday, which is
        // the day still in progress, not on an empty Saturday.
        val anchor = running ?: next

        val selection = resolveSelection(
            picked = picked,
            runningDate = running?.date,
            anchorWeekday = anchor?.day?.weekday,
            fallback = at.dayOfWeek,
        )
        picked = selection.picked
        val selected = selection.weekday

        val logical = logicalDayFor(routine, selected, anchor?.date ?: at.toLocalDate())

        _state.value = _state.value.copy(
            routine = routine,
            selected = selected,
            day = logical?.let { dayView(it, starts, at, isToday = it.date == running?.date) },
            now = at,
            resting = running == null,
            nextDayStartsAt = next?.startsAt,
        )
    }

    /**
     * The date [weekday] falls on in the week containing [anchorDate].
     *
     * Anchored on the logical day rather than the calendar date, so at 00:30 on
     * a Saturday the week is still the one Friday belongs to.
     */
    private fun logicalDayFor(routine: Routine, weekday: DayOfWeek, anchorDate: LocalDate): LogicalDay? {
        val day = routine.day(weekday) ?: return null
        val weekStart = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return LogicalDay(weekStart.plusDays((weekday.value - 1).toLong()), day)
    }

}

/**
 * Places a day's slots against the start log.
 *
 * Lifted out of the ViewModel because it is arithmetic and nothing else: given
 * a day, a log and a moment it is entirely determined, which makes it worth
 * testing directly rather than through a Hilt graph and a coroutine.
 */
internal fun dayView(
    logical: LogicalDay,
    starts: List<SlotStart>,
    at: LocalDateTime,
    isToday: Boolean,
): DayView {
    val onThisDay = starts.filter { it.on == logical.date }
    val byTime = onThisDay.map { it.at }.sorted()
    val dayIsOver = !at.isBefore(logical.endsAt)

    val rows = logical.day.slots.map { slot ->
        val plannedStart = logical.startOf(slot)
        val plannedEnd = logical.endOf(slot)
        val actualStart = onThisDay.firstOrNull { it.slotId == slot.id }?.at

        // There is no stop button: whatever was started next is what ended this
        // one. If nothing was, the slot is still open — unless the day itself
        // has finished, which closes it (v2 plan §4.4).
        val actualEnd = actualStart?.let { began ->
            byTime.firstOrNull { it.isAfter(began) } ?: logical.endsAt.takeIf { dayIsOver }
        }

        SlotRow(
            slot = slot,
            plannedStart = plannedStart,
            plannedEnd = plannedEnd,
            actualStart = actualStart,
            actualEnd = actualEnd,
            position = when {
                !at.isBefore(plannedStart) && at.isBefore(plannedEnd) -> SlotPosition.Current
                !plannedEnd.isAfter(at) -> SlotPosition.Past
                else -> SlotPosition.Upcoming
            },
        )
    }
    return DayView(logical, isToday, rows)
}

/** A resolved day choice, and the pick that survived resolving it. */
internal data class Selection(
    val weekday: DayOfWeek,
    val picked: Pair<DayOfWeek, LocalDate?>?,
)

/**
 * Which day the pager should show.
 *
 * A choice should outlast a scroll but **not** outlast the day. Keeping it
 * forever means browsing to Friday on a Tuesday leaves the tab on Friday for the
 * rest of the week, and the tab exists to answer "what now" — so a pick made
 * while a different routine day was running has expired and is dropped.
 *
 * Pure, and separate from the ViewModel, because the interesting cases are all
 * about time passing and testing them through a coroutine and a live clock would
 * prove much less.
 */
internal fun resolveSelection(
    picked: Pair<DayOfWeek, LocalDate?>?,
    runningDate: LocalDate?,
    anchorWeekday: DayOfWeek?,
    fallback: DayOfWeek,
): Selection {
    // A pick taken while nothing was running carries no date and cannot expire
    // on its own; the next running day retires it.
    val live = picked?.takeIf { (_, on) -> on != null && on == runningDate }
    return Selection(
        weekday = live?.first ?: picked?.takeIf { runningDate == null }?.first
            ?: anchorWeekday ?: fallback,
        picked = live ?: picked?.takeIf { runningDate == null },
    )
}
