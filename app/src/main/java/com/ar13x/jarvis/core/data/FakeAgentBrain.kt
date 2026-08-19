package com.ar13x.jarvis.core.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * A crude scripted stand-in for the agent, good enough to build the chat UI
 * against and no more.
 *
 * It exists to produce the *shapes* the real gateway will produce — a clarifying
 * question, then a proposal; a disambiguation list that pages three at a time —
 * so that phase C can be built and judged before the gateway exists. It is not
 * an attempt at language understanding and should never grow into one: the
 * moment it starts making decisions the real agent makes, the fake stops testing
 * the app and starts testing itself.
 */
internal object FakeAgentBrain {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Words that signal an intent rather than content, stripped from titles. */
    private val intentWords = setOf(
        "remind", "reminder", "me", "to", "about", "please", "can", "you",
        "add", "create", "new", "task", "set", "up", "a", "an", "the",
    )

    private val weekdays = mapOf(
        "monday" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
    )

    // --- Intent -----------------------------------------------------------------

    fun mentionsCompletion(text: String): Boolean =
        text.containsAny("done", "finished", "completed", "did it", "sorted")

    fun mentionsCancellation(text: String): Boolean =
        text.containsAny("cancel", "call it off", "drop it", "forget it", "never mind")

    fun mentionsReschedule(text: String): Boolean =
        text.containsAny("move", "reschedule", "push", "postpone", "shift", "change the date")

    fun mentionsPriority(text: String): Boolean =
        text.containsAny("priority", "important", "urgent", "pin")

    // --- Extraction -------------------------------------------------------------

    /**
     * Pulls a local calendar day out of the text, or null if it says nothing
     * about when. Null is the interesting case: it is what makes the fake ask a
     * clarifying question instead of guessing, which is the multi-turn behaviour
     * the parent plan measured the real model doing well (§2.4).
     */
    fun extractDate(text: String): LocalDate? {
        val lower = text.lowercase(Locale.ROOT)
        val today = LocalDate.now(zone)

        if (lower.contains("today") || lower.contains("tonight")) return today
        if (lower.contains("tomorrow")) return today.plusDays(1)
        if (lower.contains("next week")) return today.plusWeeks(1)
        if (lower.contains("next month")) return today.plusMonths(1)

        Regex("in (\\d{1,3}) days?").find(lower)?.let {
            return today.plusDays(it.groupValues[1].toLong())
        }
        Regex("in (\\d{1,2}) weeks?").find(lower)?.let {
            return today.plusWeeks(it.groupValues[1].toLong())
        }

        for ((name, day) in weekdays) {
            if (!lower.contains(name)) continue
            val adjuster = if (lower.contains("next $name")) {
                TemporalAdjusters.next(day)
            } else {
                TemporalAdjusters.nextOrSame(day)
            }
            return today.with(adjuster)
        }
        return null
    }

    /** Defaults to 9am, matching the fixtures, when a day is given without a time. */
    fun extractTime(text: String): LocalTime? {
        val lower = text.lowercase(Locale.ROOT)

        Regex("(\\d{1,2})[:.](\\d{2})\\s*(am|pm)?").find(lower)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val meridiem = match.groupValues[3]
            return LocalTime.of(hour.to24(meridiem), minute)
        }
        Regex("(\\d{1,2})\\s*(am|pm)").find(lower)?.let { match ->
            val hour = match.groupValues[1].toInt()
            return LocalTime.of(hour.to24(match.groupValues[2]), 0)
        }
        if (lower.contains("morning")) return LocalTime.of(9, 0)
        if (lower.contains("lunch") || lower.contains("midday")) return LocalTime.of(12, 30)
        if (lower.contains("afternoon")) return LocalTime.of(15, 0)
        if (lower.contains("evening") || lower.contains("tonight")) return LocalTime.of(19, 0)
        return null
    }

    /**
     * Produces the human-readable recurrence the server would render with
     * `describe_recurrence()`.
     *
     * The app never parses or generates an RRULE (plan §3.1), so the fake does
     * not either — it emits only the rendered text, which is exactly the surface
     * the app is allowed to see. Getting this wrong here would let the app grow a
     * dependency on a field it must never have.
     */
    fun extractRecurrenceText(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        if (!lower.contains("every") && !lower.contains("each") && !lower.contains("weekly") &&
            !lower.contains("daily") && !lower.contains("monthly")
        ) {
            return null
        }
        if (lower.contains("last") && lower.contains("month")) {
            for ((name, _) in weekdays) {
                if (lower.contains(name)) return "The last " + name.capitalise() + " of every month"
            }
        }
        if (lower.contains("daily") || lower.contains("every day")) return "Every day"
        if (lower.contains("weekday")) return "Every weekday"
        if (lower.contains("monthly") || lower.contains("every month")) return "Every month"

        val named = weekdays.keys.filter { lower.contains(it) }
        if (named.isNotEmpty()) {
            val pretty = named.map { it.capitalise() }
            return "Every " + when (pretty.size) {
                1 -> pretty[0]
                2 -> pretty[0] + " and " + pretty[1]
                else -> pretty.dropLast(1).joinToString(", ") + " and " + pretty.last()
            }
        }
        if (lower.contains("weekly")) return "Every week"
        return null
    }

    /** Best-effort subject line. The real model would do far better; it need not. */
    fun extractTitle(text: String): String {
        val cleaned = text
            .replace(Regex("(?i)\\b(today|tonight|tomorrow|next week|next month)\\b"), " ")
            .replace(Regex("(?i)\\b(every|each|weekly|daily|monthly)\\b.*$"), " ")
            .replace(Regex("(?i)\\bin \\d+ (days?|weeks?)\\b"), " ")
            .replace(Regex("(?i)\\b\\d{1,2}([:.]\\d{2})?\\s*(am|pm)\\b"), " ")
            .replace(Regex("(?i)\\bat\\b"), " ")
            .replace(Regex("(?i)\\bon (monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b"), " ")

        val words = cleaned
            .split(Regex("\\s+"))
            .map { it.trim { ch -> !ch.isLetterOrDigit() && ch != '\'' } }
            .filter { it.isNotBlank() }

        val leading = words.takeWhile { it.lowercase(Locale.ROOT) in intentWords }.size
        val meaningful = words.drop(leading)
        val title = (if (meaningful.isEmpty()) words else meaningful).joinToString(" ")
        return title.trim().replaceFirstChar { it.titlecase(Locale.ROOT) }.take(200)
    }

    /** Substring match over titles — the fake's stand-in for `find_tasks`. */
    fun searchTerms(text: String): List<String> = text
        .lowercase(Locale.ROOT)
        .split(Regex("[^a-z0-9']+"))
        .filter { it.length > 2 && it !in intentWords }

    private fun String.containsAny(vararg needles: String): Boolean {
        val lower = lowercase(Locale.ROOT)
        return needles.any { lower.contains(it) }
    }

    private fun Int.to24(meridiem: String): Int = when {
        meridiem == "pm" && this < 12 -> this + 12
        meridiem == "am" && this == 12 -> 0
        else -> this
    }

    private fun String.capitalise(): String =
        replaceFirstChar { it.titlecase(Locale.ROOT) }
}
