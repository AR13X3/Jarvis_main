package com.ar13x.jarvis.core.ui

import com.ar13x.jarvis.core.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Row-level date display.
 *
 * The one rule that matters here (plan §3.2): **the day comes from
 * [Task.dueDate], which the server computed for the user's local timezone.**
 * Nothing in this file derives a calendar day from [Task.dueAt].
 *
 * The *time* is a different matter and is formatted from the instant, which is
 * correct — an instant rendered in the device's zone is the wall-clock time the
 * user will experience. It is only the **day** that must not be re-derived,
 * because the server runs UTC and its day boundary is not the user's.
 */
object DueDateFormat {

    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dayThisYear = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    private val dayOtherYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    /** e.g. "Today, 9:00 AM" · "Tomorrow, 6:30 AM" · "Thu 21 Aug, 5:30 PM". */
    fun forRow(task: Task, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = timeFormat.format(task.dueAt.atZone(zone).toLocalTime())
        return day(task, zone) + ", " + time
    }

    fun day(task: Task, zone: ZoneId = ZoneId.systemDefault()): String {
        // `due_today` is the server's answer and is never second-guessed.
        if (task.dueToday) return "Today"

        val today = LocalDate.now(zone)
        // Comparing two LocalDates is safe: both are already calendar days, so
        // there is no instant-to-day conversion happening and no zone to get
        // wrong. This is the distinction §3.2 draws.
        return when (task.dueDate) {
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> {
                val format = if (task.dueDate.year == today.year) dayThisYear else dayOtherYear
                format.format(task.dueDate)
            }
        }
    }

    /** For the recurring section, which sorts and reads by next fire, not due. */
    fun nextFire(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val zoned = instant.atZone(zone)
        val today = LocalDate.now(zone)
        val date = zoned.toLocalDate()
        val day = when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> {
                val format = if (date.year == today.year) dayThisYear else dayOtherYear
                format.format(date)
            }
        }
        return day + ", " + timeFormat.format(zoned.toLocalTime())
    }

    /**
     * A to-do's deadline, or `null` when it has none — which is ordinary rather
     * than missing (§5.2): an undated to-do is what the backlog is made of.
     *
     * Same rule as everywhere else here: the **day** is [dueDate], the server's
     * own, and the **time** is read off [dueAt]. The one thing this does that
     * the task formatter does not is **omit the time when there is not one to
     * show** — [Deadline.EndOfDay] means the user picked a day and no hour, and
     * appending "11:59 PM" would invent a precision they did not ask for.
     *
     * [dueDate] is preferred and [dueAt] is only fallen back to when the server
     * sent an instant without a day. That fallback derives a calendar day on the
     * phone, which §3.2 forbids in general — it is here for the same reason
     * [forProposal] has it: the alternative is showing nothing at all for a
     * deadline that demonstrably exists, and a day that may be off by one is
     * more use than a blank. It is not expected to run.
     */
    fun forTodo(
        dueDate: LocalDate?,
        dueAt: Instant?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        if (dueDate == null && dueAt == null) return null
        val today = LocalDate.now(zone)
        val date = dueDate ?: dueAt!!.atZone(zone).toLocalDate()
        val day = when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> {
                val format = if (date.year == today.year) dayThisYear else dayOtherYear
                format.format(date)
            }
        }
        val time = dueAt
            ?.takeIf { Deadline.hasTimeOfDay(it, zone) }
            ?.let { timeFormat.format(it.atZone(zone).toLocalTime()) }
        return if (time == null) day else day + ", " + time
    }

    /**
     * The proposal summary's date, for the confirmation card.
     *
     * Same rule as a task row: the **day** comes from the server's bare
     * `due_date` and the **time** is read off the instant. A proposal is the one
     * place a wrong day is cheapest to catch and most expensive to miss, since
     * confirming it is what writes the task.
     */
    fun forProposal(
        dueDate: LocalDate?,
        dueAt: Instant?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        if (dueDate == null && dueAt == null) return null
        val today = LocalDate.now(zone)
        val date = dueDate ?: dueAt!!.atZone(zone).toLocalDate()
        val day = when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> {
                val format = if (date.year == today.year) dayThisYear else dayOtherYear
                format.format(date)
            }
        }
        val time = dueAt?.let { timeFormat.format(it.atZone(zone).toLocalTime()) }
        return if (time == null) day else day + ", " + time
    }
}
