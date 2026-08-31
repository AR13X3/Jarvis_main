package com.ar13x.jarvis.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private const val MINUTES_PER_DAY = 24 * 60

/**
 * Placing a routine against real time.
 *
 * **The whole file exists because the day does not end at midnight.** Friday's
 * Speedway slot runs 13:45 to 00:15 and two more slots follow it; Thursday
 * starts at 10:00; Friday to Sunday run until 02:00. Bucket any of that by
 * calendar date and three hours of Friday land on Saturday, and every weekend
 * number is quietly wrong.
 *
 * So nothing here asks what date a timestamp falls on. Everything is measured
 * as *minutes from the declared start of a logical day*, which makes midnight
 * an ordinary point in the middle rather than a boundary.
 *
 * This is `jarvis-app-plan.md` §3.2 in another costume, and the answer is the
 * same: the shape of a day is declared, never derived.
 *
 * These functions compute over the *template*, which is deterministic and local.
 * Adherence — what was actually started, what was missed, what is drifting —
 * comes from the gateway and is never computed here.
 */

/** A routine day placed on a real date. [date] is the date the day *began*. */
data class LogicalDay(
    val date: LocalDate,
    val day: RoutineDay,
) {
    val startsAt: LocalDateTime get() = date.atTime(day.startsAt)

    val endsAt: LocalDateTime
        get() = startsAt.plusMinutes(lengthMinutes.toLong())

    val lengthMinutes: Int
        get() = spanMinutes(day.startsAt, day.endsAt)

    /** Where [at] falls in this day, in minutes from its start. */
    fun offsetOf(at: LocalDateTime): Long = java.time.Duration.between(startsAt, at).toMinutes()

    fun contains(at: LocalDateTime): Boolean =
        !at.isBefore(startsAt) && at.isBefore(endsAt)

    /** The instant [slot] begins on this date. */
    fun startOf(slot: RoutineSlot): LocalDateTime =
        startsAt.plusMinutes(offsetMinutes(day.startsAt, slot.start).toLong())

    fun endOf(slot: RoutineSlot): LocalDateTime =
        startOf(slot).plusMinutes(slot.durationMinutes.toLong())
}

/**
 * How long a slot runs, in minutes, crossing midnight where it needs to.
 *
 * 13:45 to 00:15 is ten and a half hours, not a negative number.
 */
val RoutineSlot.durationMinutes: Int get() = spanMinutes(start, end)

/**
 * The logical day containing [now], or null when no day is running.
 *
 * Null is a real state, not a failure: between 02:00 and 08:00 on a Saturday
 * nobody's routine is active, and the honest thing to show is when the next one
 * begins rather than pretending a day is underway.
 *
 * Two candidates are checked — today, and yesterday, because yesterday's day
 * may still be running at 00:30. Checking only today is the bug this file
 * exists to prevent.
 */
fun Routine.logicalDayAt(now: LocalDateTime): LogicalDay? {
    val today = now.toLocalDate()
    for (date in listOf(today.minusDays(1), today)) {
        val day = day(date.dayOfWeek) ?: continue
        val logical = LogicalDay(date, day)
        if (logical.contains(now)) return logical
    }
    return null
}

/** The next logical day to begin after [now]. Used when nothing is running. */
fun Routine.nextDayAfter(now: LocalDateTime): LogicalDay? {
    val today = now.toLocalDate()
    for (offset in 0..7L) {
        val date = today.plusDays(offset)
        val day = day(date.dayOfWeek) ?: continue
        val logical = LogicalDay(date, day)
        if (logical.startsAt.isAfter(now)) return logical
    }
    return null
}

/**
 * A day split around the present moment — the shape the day view renders.
 *
 * Upcoming comes first on screen, so the split is the screen's structure rather
 * than a filter applied to it.
 */
data class DayProgress(
    val logical: LogicalDay,
    val past: List<RoutineSlot>,
    val current: RoutineSlot?,
    val upcoming: List<RoutineSlot>,
) {
    val trackedTotal: Int get() = logical.day.trackedSlots.size
}

/**
 * Splits [logical]'s slots around [now].
 *
 * A day in the past is entirely past and a day in the future is entirely
 * upcoming, so this answers correctly for any day of the week rather than only
 * for today — which is what lets the pager show Thursday on a Monday.
 */
fun splitAround(logical: LogicalDay, now: LocalDateTime): DayProgress {
    val past = mutableListOf<RoutineSlot>()
    val upcoming = mutableListOf<RoutineSlot>()
    var current: RoutineSlot? = null

    for (slot in logical.day.slots) {
        val start = logical.startOf(slot)
        val end = logical.endOf(slot)
        when {
            !now.isBefore(start) && now.isBefore(end) -> current = slot
            end <= now -> past += slot
            else -> upcoming += slot
        }
    }
    return DayProgress(logical, past, current, upcoming)
}

// --- template arithmetic ------------------------------------------------------

/** Minutes per category across one day. */
fun RoutineDay.minutesByCategory(): Map<String, Int> =
    slots.groupBy { it.categoryId }.mapValues { (_, group) -> group.sumOf { it.durationMinutes } }

/** Minutes per category across the whole week. */
fun Routine.weeklyMinutesByCategory(): Map<String, Int> {
    val totals = mutableMapOf<String, Int>()
    for (day in days) {
        for ((id, minutes) in day.minutesByCategory()) {
            totals[id] = (totals[id] ?: 0) + minutes
        }
    }
    return totals
}

/** The headline committed / upkeep / free split, in minutes. */
fun Routine.weeklyMinutesByClass(): Map<CategoryClass, Int> {
    val totals = mutableMapOf<CategoryClass, Int>()
    for ((id, minutes) in weeklyMinutesByCategory()) {
        val cls = category(id)?.cls ?: continue
        totals[cls] = (totals[cls] ?: 0) + minutes
    }
    return totals
}

/**
 * Forward distance between two times of day, wrapping past midnight, where
 * **equal times mean zero**. Positions something inside a day.
 *
 * The first slot of every day starts at the moment the day starts, so this is
 * the reading an offset needs — and the opposite of the one a span needs.
 */
private fun offsetMinutes(from: LocalTime, to: LocalTime): Int {
    val delta = to.toSecondOfDay() / 60 - from.toSecondOfDay() / 60
    return if (delta < 0) delta + MINUTES_PER_DAY else delta
}

/**
 * Forward distance between two times of day, wrapping past midnight, where
 * **equal times mean a full day**. Measures how long something lasts.
 *
 * A routine day is bounded by waking and sleeping, so a day declared 08:00 to
 * 08:00 is twenty-four hours rather than nothing.
 *
 * Kept separate from [offsetMinutes] deliberately, because one helper served
 * both at first and the two disagree on exactly one input — equality — which is
 * the input every day hits on its very first slot. Every day therefore drew
 * itself twenty-four hours late, and both the tiling test and the split test
 * caught it.
 */
private fun spanMinutes(from: LocalTime, to: LocalTime): Int {
    val delta = to.toSecondOfDay() / 60 - from.toSecondOfDay() / 60
    return if (delta <= 0) delta + MINUTES_PER_DAY else delta
}
