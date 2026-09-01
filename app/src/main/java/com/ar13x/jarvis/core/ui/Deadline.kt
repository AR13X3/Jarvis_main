package com.ar13x.jarvis.core.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Turning a day somebody tapped into the instant the gateway stores.
 *
 * **The app sends `due_at` and never `due_date`** (plan §3.2). `due_date` and
 * `starts_on` are the server's to compute, because the server knows the zone the
 * user's calendar days are measured in and the phone only knows its own. So the
 * one direction this file travels is *day + time → instant*; nothing here turns
 * a stored instant back into a **day** for display, and [DueDateFormat] is where
 * that rule is enforced.
 *
 * There is one legitimate instant → local read here, [timeOf], and it is a
 * *time* rather than a day. An instant rendered in the device's zone is the
 * wall-clock moment the user will actually experience, which is exactly what
 * they picked; it is only the calendar day whose boundary the phone and the
 * server can disagree about.
 */
object Deadline {

    /**
     * The time of day a deadline gets when the user picked a **day and no hour**.
     *
     * **23:59 local, not midnight, and this is a decision rather than a
     * convention.** Two reasons, and the second is the one that would have bitten:
     *
     * 1. "Due Friday" means *by the end of Friday*. A deadline at 00:00 on Friday
     *    is overdue for the whole of the day it is due, which is not what anybody
     *    means and would have made the dashboard's lateness numbers nonsense.
     *
     * 2. **It is the choice that survives being wrong about the server's zone.**
     *    The gateway derives `due_date` from `due_at` in a configured local zone
     *    — that is what the contract's own `ProposalSummary` note says, and it is
     *    why the phone must not derive the day itself. If that zone were ever
     *    UTC rather than Sydney's, a midnight-local deadline would come back
     *    dated **the day before**: 00:00 on 5 Sep in Sydney is 14:00 on 4 Sep in
     *    UTC. 23:59 local is 13:59 UTC on the same date, so it round-trips under
     *    either reading. Picking the sentinel that is right under both beats
     *    picking the one that is right under an assumption nobody has measured.
     *
     * It doubles as the marker for "no particular time" — see [hasTimeOfDay] —
     * so the row can show a bare day instead of a meaningless "11:59 PM".
     */
    val EndOfDay: LocalTime = LocalTime.of(23, 59)

    /**
     * The instant to send for a deadline on [day] at [time].
     *
     * `atZone` resolves a time that a DST spring-forward skipped by moving it
     * forward, which is the right answer here and is also unreachable in
     * practice: gaps are cut around 2am and [EndOfDay] is not.
     */
    fun instantOf(
        day: LocalDate,
        time: LocalTime = EndOfDay,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Instant = day.atTime(time).atZone(zone).toInstant()

    /**
     * The wall-clock time of a stored deadline, to seed the time picker.
     *
     * Truncated to the minute because that is the resolution the picker offers;
     * without it a deadline the agent wrote with seconds on it would come back
     * from the picker one round trip later having silently lost them, and
     * [hasTimeOfDay] would flip for a reason nobody could see.
     */
    fun timeOf(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        instant.atZone(zone).toLocalTime().withSecond(0).withNano(0)

    /**
     * Whether this deadline names an hour, or only a day.
     *
     * A to-do due "Friday" and one due "Friday at 6:30" are different promises
     * and should not read the same on a row. The distinction is carried by
     * [EndOfDay] rather than by a second column, because the contract has no
     * field for it and inventing one would mean asking gw03 for a migration to
     * store something the sentinel already says.
     *
     * **The known cost, stated rather than discovered later:** a deadline the
     * *agent* happens to set at exactly 23:59 through chat will show as a bare
     * day. That is one minute in 1440, it degrades to the less specific of two
     * true readings, and the alternative was a schema change.
     */
    fun hasTimeOfDay(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        timeOf(instant, zone) != EndOfDay
}
