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

/**
 * The logical day a moment should be *recorded against*, and whether reaching it
 * required clamping.
 *
 * 48 hours a week belong to no logical day: the week is 120 waking hours out of
 * 168, and the gaps are 01:00→08:00 four times, 01:00→10:00 on Wednesday night,
 * and 02:00→08:00 three times. So [logicalDayAt] is a *partial* function and a
 * tap at 3am on a Saturday lands in a hole.
 *
 * **A 3am tap is a late finish, not a new day.** It is attributed to the day
 * that just ended, and [Attribution.clamped] records that it was — because
 * dropping it would lose the one honest signal about overrun, which is the whole
 * reason for tracking three consecutive Speedway nights in the first place.
 *
 * No threshold, deliberately. "Within N hours of the end" needs an N that nobody
 * can justify, and it puts a discontinuity in the middle of the night, which is
 * exactly where the interesting data lives.
 */
data class Attribution(val day: LogicalDay, val clamped: Boolean)

fun Routine.attribute(now: LocalDateTime): Attribution? {
    logicalDayAt(now)?.let { return Attribution(it, clamped = false) }

    // Scan backwards to the most recently ended day. Backwards, never forwards
    // from midnight: every day's end precedes the next day's start, so the
    // interval is unambiguous wherever it is defined at all.
    for (offset in 0..7L) {
        val date = now.toLocalDate().minusDays(offset)
        val day = day(date.dayOfWeek) ?: continue
        val logical = LogicalDay(date, day)
        if (!logical.endsAt.isAfter(now)) return Attribution(logical, clamped = true)
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
