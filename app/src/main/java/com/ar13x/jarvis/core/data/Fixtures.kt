package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Fixture data for phases A–C.
 *
 * The fake stands in for the *gateway*, so it is the one place in the app
 * allowed to derive `due_date` and `due_today` from a clock — that derivation is
 * the server's job (plan §3.2), and doing it here rather than in a ViewModel is
 * what keeps the rule honest when the Retrofit implementation replaces it.
 *
 * The set is chosen to put every visual state on screen at once: all five
 * statuses, a priority-and-recurring task (the §5.2 open question, built
 * duplicated so it can be judged on the device), a title long enough to wrap,
 * and enough filler to make 20-per-page paging real.
 */
internal object Fixtures {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun at(date: LocalDate, time: LocalTime): Instant =
        date.atTime(time).atZone(zone).toInstant()

    private fun task(
        id: Long,
        title: String,
        dueDate: LocalDate,
        dueTime: LocalTime = LocalTime.of(9, 0),
        description: String = "",
        isPriority: Boolean = false,
        recurrence: String? = null,
        recurrenceText: String? = null,
        nextFireAt: Instant? = null,
        status: TaskStatus = TaskStatus.Active,
        createdDaysAgo: Long = 3,
    ): Task {
        val today = LocalDate.now(zone)
        val dueAt = at(dueDate, dueTime)
        return Task(
            id = id,
            title = title,
            description = description,
            dueAt = dueAt,
            dueDate = dueDate,
            isPriority = isPriority,
            recurrence = recurrence,
            recurrenceText = recurrenceText,
            nextFireAt = nextFireAt ?: if (recurrence != null) dueAt else null,
            status = status,
            // Server-computed against the user's LOCAL day, never from the instant.
            dueToday = dueDate == today && !status.isTerminal,
            createdAt = at(today.minusDays(createdDaysAgo), LocalTime.of(12, 0)),
            updatedAt = at(today.minusDays(createdDaysAgo), LocalTime.of(12, 0)),
            completedAt = if (status == TaskStatus.Completed) at(today.minusDays(1), LocalTime.NOON) else null,
            cancelledAt = if (status == TaskStatus.Cancelled) at(today.minusDays(2), LocalTime.NOON) else null,
        )
    }

    fun seed(): List<Task> {
        val today = LocalDate.now(zone)
        val curated = listOf(
            task(
                id = 1,
                title = "Call the dentist",
                dueDate = today,
                dueTime = LocalTime.of(9, 0),
                description = "Ask about the crown they mentioned last time.",
                isPriority = true,
            ),
            task(
                id = 2,
                title = "Renew the car registration before it lapses",
                dueDate = today,
                dueTime = LocalTime.of(17, 30),
                isPriority = true,
                status = TaskStatus.Awaiting,
            ),
            // Priority AND recurring — appears in both sections on purpose.
            // Build it duplicated, look at it on the device, then decide (§5.2).
            task(
                id = 3,
                title = "Weekly review",
                dueDate = today.plusDays(2),
                dueTime = LocalTime.of(18, 0),
                isPriority = true,
                recurrence = "FREQ=WEEKLY;BYDAY=FR",
                recurrenceText = "Every Friday",
            ),
            task(
                id = 4,
                title = "Gym",
                dueDate = today.plusDays(1),
                dueTime = LocalTime.of(6, 30),
                recurrence = "FREQ=WEEKLY;BYDAY=MO,WE,FR",
                recurrenceText = "Every Monday, Wednesday and Friday",
            ),
            task(
                id = 5,
                title = "Pay the rates notice",
                dueDate = today.withDayOfMonth(1).plusMonths(1),
                recurrence = "FREQ=MONTHLY;BYSETPOS=-1;BYDAY=FR",
                recurrenceText = "The last Friday of every month",
            ),
            // A lapse, not a failure — still fully mutable, and the one you most
            // want to reschedule (§4.3).
            task(
                id = 6,
                title = "Send the insurance paperwork",
                dueDate = today.minusDays(3),
                status = TaskStatus.Incomplete,
                createdDaysAgo = 9,
            ),
            // A decision, not an error. The row persists; rows are never deleted.
            task(
                id = 7,
                title = "Book the Thursday flight",
                dueDate = today.minusDays(2),
                status = TaskStatus.Cancelled,
                createdDaysAgo = 8,
            ),
            task(
                id = 8,
                title = "Pick up the prescription",
                dueDate = today.minusDays(1),
                status = TaskStatus.Completed,
                createdDaysAgo = 6,
            ),
            // Long enough to wrap and stress the row layout.
            task(
                id = 9,
                title = "Ask the strata manager about the water damage in the basement car park",
                dueDate = today.plusDays(4),
                dueTime = LocalTime.of(11, 15),
            ),
            // Three same-titled tasks on different dates — the disambiguation
            // fixture for acceptance item 7 (§12).
            task(id = 10, title = "Dinner with Sam", dueDate = today.plusDays(1), dueTime = LocalTime.of(19, 0)),
            task(id = 11, title = "Dinner with Sam", dueDate = today.plusDays(8), dueTime = LocalTime.of(19, 0)),
            task(id = 12, title = "Dinner with Sam", dueDate = today.plusDays(15), dueTime = LocalTime.of(19, 30)),
        )

        // Filler, so 20-per-page paging is exercised rather than assumed.
        val filler = (13..46).map { i ->
            task(
                id = i.toLong(),
                title = fillerTitles[(i - 13) % fillerTitles.size],
                dueDate = today.plusDays(((i % 21) - 7).toLong()),
                dueTime = LocalTime.of(8 + (i % 10), if (i % 2 == 0) 0 else 30),
                status = when (i % 7) {
                    0 -> TaskStatus.Completed
                    5 -> TaskStatus.Incomplete
                    6 -> TaskStatus.Cancelled
                    else -> TaskStatus.Active
                },
                createdDaysAgo = (i % 30).toLong(),
            )
        }
        return curated + filler
    }

    private val fillerTitles = listOf(
        "Water the plants",
        "Back up the laptop",
        "Reply to the landlord",
        "Order more coffee",
        "Service the bike",
        "Check the smoke alarms",
        "Return the library books",
        "Update the passport photo",
        "Clean the rangehood filter",
        "Chase the tax receipt",
        "Book the dog groomer",
        "Fix the leaking tap",
        "Sort the winter clothes",
        "Call Mum",
        "Renew the domain",
        "Descale the kettle",
        "Photograph the meter reading",
    )
}
