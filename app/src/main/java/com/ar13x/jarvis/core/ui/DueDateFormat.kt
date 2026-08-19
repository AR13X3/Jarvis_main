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
}
