package com.ar13x.jarvis.core.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Setting a deadline, which the app could not do at all until §9.1.
 *
 * Two separate things are pinned here and they fail in different ways:
 *
 * 1. **The end-of-day sentinel is the choice that survives being wrong about
 *    the server's timezone.** The gateway derives `due_date` from the instant
 *    the app sends, in a zone the app cannot see. If that zone is Sydney's,
 *    any time of day round-trips. If it is UTC — which the app has no way to
 *    rule out — only the late ones do. 23:59 is right under both readings and
 *    midnight is right under only one, and the test for the *midnight* case is
 *    here deliberately: it records what would have gone wrong, so nobody
 *    "simplifies" the sentinel to `LocalTime.MIDNIGHT` later and finds every
 *    deadline landing a day early with the time still perfectly correct.
 *
 * 2. **A day is not an hour.** Most to-dos want a date; showing "11:59 PM" on
 *    every one of them would invent a precision nobody asked for.
 */
class DeadlineTest {

    private val sydney: ZoneId = ZoneId.of("Australia/Sydney")
    private lateinit var original: TimeZone

    @Before
    fun setUp() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(sydney))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(original)
    }

    /** The UTC calendar day of an instant — how a UTC-run server would date it. */
    private fun utcDayOf(instant: Instant): LocalDate =
        instant.atZone(ZoneOffset.UTC).toLocalDate()

    // --- 1. the sentinel ----------------------------------------------------

    @Test
    fun `end of day in Sydney still falls on the same calendar day in UTC`() {
        val friday = LocalDate.of(2026, 9, 4)
        val instant = Deadline.instantOf(friday, Deadline.EndOfDay, sydney)

        // 23:59 on 4 Sep in Sydney (UTC+10 in September) is 13:59 on 4 Sep UTC.
        assertEquals(Instant.parse("2026-09-04T13:59:00Z"), instant)
        assertEquals(friday, utcDayOf(instant))
    }

    @Test
    fun `midnight would have landed on the day before, which is why it is not the sentinel`() {
        val friday = LocalDate.of(2026, 9, 4)
        val instant = Deadline.instantOf(friday, LocalTime.MIDNIGHT, sydney)

        // 00:00 on 4 Sep in Sydney is 14:00 on 3 SEPTEMBER in UTC.
        assertEquals(Instant.parse("2026-09-03T14:00:00Z"), instant)
        assertNotEquals(friday, utcDayOf(instant))
        assertEquals(friday.minusDays(1), utcDayOf(instant))
    }

    @Test
    fun `the sentinel survives daylight saving, when the offset is an hour larger`() {
        // Sydney goes to UTC+11 in October. 23:59 is 12:59Z — still the same day.
        val summer = LocalDate.of(2026, 12, 4)
        val instant = Deadline.instantOf(summer, Deadline.EndOfDay, sydney)

        assertEquals(Instant.parse("2026-12-04T12:59:00Z"), instant)
        assertEquals(summer, utcDayOf(instant))
    }

    @Test
    fun `a picked time comes back as the same wall clock time`() {
        val day = LocalDate.of(2026, 9, 4)
        val at = LocalTime.of(6, 30)

        assertEquals(at, Deadline.timeOf(Deadline.instantOf(day, at, sydney), sydney))
    }

    @Test
    fun `seconds on a stored deadline do not survive into the picker`() {
        // The picker offers minutes. Without truncation a deadline the agent
        // wrote with seconds on it would flip `hasTimeOfDay` for no visible
        // reason the moment it round-tripped.
        val instant = Instant.parse("2026-09-04T13:59:41Z")

        assertEquals(LocalTime.of(23, 59), Deadline.timeOf(instant, sydney))
    }

    // --- 2. a day is not an hour --------------------------------------------

    @Test
    fun `the sentinel means no time of day, and any other time means there is one`() {
        val day = LocalDate.of(2026, 9, 4)

        assertFalse(Deadline.hasTimeOfDay(Deadline.instantOf(day, Deadline.EndOfDay, sydney), sydney))
        assertTrue(Deadline.hasTimeOfDay(Deadline.instantOf(day, LocalTime.of(6, 30), sydney), sydney))
        assertTrue(Deadline.hasTimeOfDay(Deadline.instantOf(day, LocalTime.MIDNIGHT, sydney), sydney))
    }

    /**
     * The day string itself is the formatter's business and shifts with the
     * locale and the current year — en-AU renders "Fri 4 Sept", en-US "Fri 4
     * Sep", and both switch to a year-bearing form once the date is not in this
     * one. Asserting a literal here pins the locale rather than the behaviour,
     * so the canonical rendering is asked for through the public API instead.
     */
    private fun dayOnly(day: LocalDate): String? = DueDateFormat.forTodo(day, null, sydney)

    @Test
    fun `a day-only deadline renders without a time`() {
        val day = LocalDate.of(2026, 9, 4)
        val shown = DueDateFormat.forTodo(day, Deadline.instantOf(day, Deadline.EndOfDay, sydney), sydney)

        assertEquals(dayOnly(day), shown)
        assertFalse("no invented precision: " + shown, shown!!.contains(":"))
    }

    @Test
    fun `a deadline with an hour renders with it`() {
        val day = LocalDate.of(2026, 9, 4)
        val shown = DueDateFormat.forTodo(day, Deadline.instantOf(day, LocalTime.of(18, 30), sydney), sydney)

        assertEquals(dayOnly(day), shown!!.substringBefore(","))
        assertTrue(shown, shown.contains("6:30"))
    }

    @Test
    fun `no deadline is null rather than a placeholder`() {
        assertNull(DueDateFormat.forTodo(null, null, sydney))
    }

    // --- 3. §3.2, the rule this screen could most easily have broken ---------

    @Test
    fun `the day shown is the server's, even when the instant reads as another one`() {
        // 2026-09-05T20:00Z is 06:00 on the SIXTH in Sydney. The server says the
        // fifth. The server wins — that is the whole of §3.2, and the time is
        // still read off the instant because a time has no day boundary to
        // disagree about.
        val serverDay = LocalDate.of(2026, 9, 5)
        val instant = Instant.parse("2026-09-05T20:00:00Z")

        val shown = DueDateFormat.forTodo(serverDay, instant, sydney)!!

        assertEquals(dayOnly(serverDay), shown.substringBefore(","))
        assertNotEquals(dayOnly(serverDay.plusDays(1)), shown.substringBefore(","))
        assertTrue(shown, shown.contains("6:00"))
    }

    @Test
    fun `today and tomorrow are named rather than dated`() {
        val today = LocalDate.now(sydney)
        val atNine = Deadline.instantOf(today, LocalTime.of(9, 0), sydney)

        assertTrue(DueDateFormat.forTodo(today, atNine, sydney)!!.startsWith("Today,"))
        assertEquals(
            "Tomorrow",
            DueDateFormat.forTodo(
                today.plusDays(1),
                Deadline.instantOf(today.plusDays(1), Deadline.EndOfDay, sydney),
                sydney,
            ),
        )
    }
}
