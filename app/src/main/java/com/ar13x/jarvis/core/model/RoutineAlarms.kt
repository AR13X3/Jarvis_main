package com.ar13x.jarvis.core.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One tracked slot, placed on a real date, ready to be armed (§9.6).
 *
 * Everything an alarm needs is here, because **everything an alarm needs has to
 * survive process death**. `RemoteRoutineRepository` caches the routine in a
 * `MutableStateFlow` and nothing more, so a broadcast receiver waking a cold
 * process has no routine at all. The label and the times therefore travel in the
 * alarm's extras rather than being looked up when it fires.
 *
 * That is the opposite of what `AlarmReceiver` does for reminders — it reads the
 * title from the Room mirror so a renamed task fires under its current name —
 * and the difference is deliberate. A reminder's mirror is on disk; the routine's
 * cache is not. Staleness here is bounded by the arming window instead.
 */
data class SlotAlarm(
    /**
     * The **logical** day this slot belongs to, which is not always the calendar
     * date of [at]: Friday's Speedway shift runs to 00:15 and the two slots after
     * it are still Friday's.
     *
     * This is what gets recorded as `SlotStart.on`, and it is carried rather
     * than recomputed for the same reason the day view passes the day on screen:
     * the day is declared, never derived from the clock (§3.2, v2 §4.2).
     */
    val on: LocalDate,
    val slotId: String,
    val label: String,
    /** Wall-clock start. */
    val at: LocalDateTime,
    /** Wall-clock end of the slot. Shown in the notification, never armed on. */
    val endsAt: LocalDateTime,
    /**
     * When the logical day this slot belongs to ends.
     *
     * Carried so the Start action can decide `clamped` without the routine and
     * without the network — see `SlotStartReceiver`.
     */
    val dayEndsAt: LocalDateTime,
)

/**
 * The tracked slots starting within [within] of [now], soonest first.
 *
 * **Tracked only, and that is a hard requirement rather than a filter.** v2 plan
 * §4.3 splits slots into tracked, scaffold, buffer and free; there are about 3.7
 * tracked ones a day against a day that is partitioned end to end. Arming every
 * slot would be roughly eighty interruptions a week, which does not get tuned —
 * it gets the whole feature switched off, and that is worse than never having
 * built it.
 *
 * **Days are walked, not dates.** The scan starts at yesterday because a logical
 * day that began yesterday can still be running now — Friday runs to 02:00 —
 * and the slots after midnight belong to it. Bucketing by calendar date is the
 * bug `RoutineClock` exists to prevent, and it would put three hours of Friday
 * night on Saturday.
 *
 * Only future starts are returned. A slot that began ten minutes ago has not
 * been missed by this function — the day view still shows it and it can still be
 * started — but arming an alarm for a moment that has passed would fire it
 * immediately, which is the same guard `AlarmScheduler.reconcile` makes.
 */
fun Routine.trackedSlotAlarms(
    now: LocalDateTime,
    within: Duration = Duration.ofHours(48),
): List<SlotAlarm> {
    val until = now.plus(within)
    val alarms = mutableListOf<SlotAlarm>()

    // Yesterday through the far end of the window, DERIVED from `within` rather
    // than fixed at the 48 hours the caller usually passes.
    //
    // It was `-1..3`, which is right for 48 hours and silently wrong for
    // anything longer: `RoutineAlarmScheduler` sweeps four days when it cancels,
    // and the last day of that sweep was never generated. Nothing visibly broke
    // — the sweep only ever cancels, so it under-cancelled — but a helper whose
    // window argument is quietly capped is one that will be trusted with a
    // bigger number later.
    //
    // Yesterday at the near end because a logical day that began yesterday can
    // still be running now; `+ 1` at the far end because a day beginning on the
    // last date in range can hold slots that start within the window.
    val lastOffset = within.toDays() + 1
    for (offset in -1L..lastOffset) {
        val date = now.toLocalDate().plusDays(offset)
        val day = day(date.dayOfWeek) ?: continue
        val logical = LogicalDay(date, day)

        for (slot in day.trackedSlots) {
            val at = logical.startOf(slot)
            if (at.isAfter(now) && !at.isAfter(until)) {
                alarms += SlotAlarm(
                    on = logical.date,
                    slotId = slot.id,
                    label = slot.label,
                    at = at,
                    endsAt = logical.endOf(slot),
                    dayEndsAt = logical.endsAt,
                )
            }
        }
    }

    return alarms.sortedBy { it.at }
}
