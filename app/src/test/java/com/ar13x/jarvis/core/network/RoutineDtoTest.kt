package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.model.CategoryClass
import com.ar13x.jarvis.core.model.SlotKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * `GET /routine` and `/routine/starts`, decoded and mapped.
 *
 * The weekday tests are the reason this file exists. `RoutineDay.weekday` is a
 * bare `integer` in the contract with no range and no base, Python has both
 * conventions one letter apart (`weekday()` is 0 = Monday, `isoweekday()` is
 * 1 = Monday), and choosing wrong shifts the whole week by a day while leaving
 * every slot and every time correct. It would look like bad data, not a client
 * bug, and nobody would think to check the client.
 */
class RoutineDtoTest {

    private val sydney: ZoneId = ZoneId.of("Australia/Sydney")

    private fun day(weekday: Int, note: String? = null) = RoutineDayDto(
        weekday = weekday,
        startsAt = LocalTime.of(7, 30),
        endsAt = LocalTime.of(1, 0),
        note = note,
        slots = listOf(
            RoutineSlotDto(
                key = "wake",
                label = "Wake",
                startsAt = LocalTime.of(7, 30),
                endsAt = LocalTime.of(8, 45),
                categoryKey = "life",
                kind = "scaffold",
                position = 0,
            ),
            RoutineSlotDto(
                key = "reskill",
                label = "Reskill",
                startsAt = LocalTime.of(8, 45),
                endsAt = LocalTime.of(12, 0),
                categoryKey = "reskill",
                kind = "tracked",
                position = 1,
            ),
        ),
    )

    private fun routine(vararg weekdays: Int) = RoutineDto(
        routineId = 1,
        name = "The week",
        versionId = 4,
        effectiveFrom = LocalDate.of(2026, 9, 1),
        categories = listOf(RoutineCategoryDto("reskill", "Reskill", "committed")),
        days = weekdays.map { day(it) },
    )

    // --- the weekday base ------------------------------------------------------

    @Test
    fun `a one-based week maps Monday to Monday`() {
        val days = routine(1, 2, 3, 4, 5, 6, 7).toDomain().days

        assertEquals(DayOfWeek.MONDAY, days.first().weekday)
        assertEquals(DayOfWeek.SUNDAY, days.last().weekday)
    }

    @Test
    fun `a zero-based week maps Monday to Monday too`() {
        // Python's `date.weekday()`. The same seven days, numbered one lower,
        // and the result must be identical — if this returned Sunday-to-Saturday
        // the entire routine would be off by one with nothing on screen to
        // suggest it.
        val days = routine(0, 1, 2, 3, 4, 5, 6).toDomain().days

        assertEquals(DayOfWeek.MONDAY, days.first().weekday)
        assertEquals(DayOfWeek.SUNDAY, days.last().weekday)
    }

    @Test
    fun `the two encodings produce identical routines`() {
        assertEquals(
            routine(1, 2, 3, 4, 5, 6, 7).toDomain(),
            routine(0, 1, 2, 3, 4, 5, 6).toDomain(),
        )
    }

    @Test
    fun `a set that is neither encoding throws rather than being coerced`() {
        // The case where a guess would do the most damage and be least visible.
        // 0 and 7 together cannot both be Monday under either convention, so the
        // payload is not something this code understands — and quietly picking
        // one would produce a plausible, wrong week.
        val thrown = runCatching { routine(0, 7).toDomain() }.exceptionOrNull()

        assertNotNull("an ambiguous weekday set must not be coerced", thrown)
        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `a partial week is still decidable when it is unambiguous`() {
        // Weekdays only, one-based. 1..5 is inside 1..7 and outside 0..6 is
        // false — 1..5 fits BOTH, and the tie is broken toward one-based, which
        // is what a partial payload has to do. Recorded rather than left
        // implicit, because it is the one case the data cannot settle.
        assertEquals(DayOfWeek.MONDAY, routine(1, 2, 3, 4, 5).toDomain().days.first().weekday)
    }

    // --- the rest of the mapping ------------------------------------------------

    @Test
    fun `slots are ordered by position, not by arrival or by clock time`() {
        // The day view splits the day on list order. Sorting by start time would
        // be wrong for the days that matter most: Friday's 13:45-00:15 shift is
        // followed by two slots whose times are numerically earlier.
        val scrambled = day(1).copy(slots = day(1).slots.reversed())

        val slots = scrambled.toDomain(base = 1).slots

        assertEquals(listOf("wake", "reskill"), slots.map { it.id })
    }

    @Test
    fun `an unknown slot kind falls back to Free, never to Tracked`() {
        // The safe direction. An unknown kind that became Tracked would start
        // appearing in adherence numbers as a slot nobody can tick, and it would
        // count as missed every single day.
        val slot = day(1).slots.first().copy(kind = "something-new")

        assertEquals(SlotKind.Free, slot.toDomain().kind)
    }

    @Test
    fun `keys become ids and the category class is mapped`() {
        val routine = routine(1).toDomain()

        assertEquals(1L, routine.routineId)
        assertEquals(4L, routine.versionId)
        assertEquals("reskill", routine.categories.single().id)
        assertEquals(CategoryClass.Committed, routine.categories.single().cls)
        assertEquals("reskill", routine.days.single().slots[1].categoryId)
        assertEquals(SlotKind.Tracked, routine.days.single().slots[1].kind)
    }

    @Test
    fun `a day whose boundaries cross midnight is preserved, not normalised`() {
        // 07:30 to 01:00. A client that treated the earlier end as an error, or
        // as midnight, would silently delete the late slots — which the contract
        // warns about in its own words.
        val d = routine(1).toDomain().days.single()

        assertEquals(LocalTime.of(7, 30), d.startsAt)
        assertEquals(LocalTime.of(1, 0), d.endsAt)
        assertTrue(d.crossesMidnight)
    }

    @Test
    fun `the drift tolerance is null until the gateway sends one`() {
        // Tracker 109. Null here means `SlotRow.drifted` is null, which the day
        // view renders as no verdict — not as "on time".
        assertNull(routine(1).toDomain().driftToleranceMinutes)
        assertEquals(15, routine(1).copy(driftThresholdMinutes = 15).toDomain().driftToleranceMinutes)
    }

    // --- starts -----------------------------------------------------------------

    @Test
    fun `a start keeps the server's day and takes only its time from the instant`() {
        // The case §4.2 exists for. 2026-09-12T14:05Z is 00:05 on Saturday in
        // Sydney, and it belongs to FRIDAY's routine day — which no arithmetic
        // on the instant could work out. The day comes from `on`; only the
        // clock time is read off the timestamp.
        val dto = SlotStartDto(
            versionId = 4,
            slotKey = "speedway",
            on = LocalDate.of(2026, 9, 11),
            startedAt = Instant.parse("2026-09-11T14:05:00Z"),
        )

        val start = dto.toDomain(sydney)

        assertEquals(LocalDate.of(2026, 9, 11), start.on)
        assertEquals(DayOfWeek.FRIDAY, start.on.dayOfWeek)
        assertEquals(LocalTime.of(0, 5), start.at.toLocalTime())
        // The wall-clock DATE is Saturday, and that is fine and unused — `on` is
        // what the day view buckets on.
        assertEquals(LocalDate.of(2026, 9, 12), start.at.toLocalDate())
        assertEquals(4L, start.versionId)
        assertEquals("speedway", start.slotId)
    }

    @Test
    fun `the zone is a parameter, so the same instant reads differently elsewhere`() {
        val dto = SlotStartDto(
            versionId = 4,
            slotKey = "gym",
            on = LocalDate.of(2026, 9, 11),
            startedAt = Instant.parse("2026-09-11T14:05:00Z"),
        )

        assertEquals(LocalTime.of(0, 5), dto.toDomain(sydney).at.toLocalTime())
        assertEquals(LocalTime.of(14, 5), dto.toDomain(ZoneId.of("UTC")).at.toLocalTime())
    }

    // --- decoding ----------------------------------------------------------------

    @Test
    fun `times decode with and without seconds`() {
        // FastAPI emits "08:45:00"; the routine's own times are minute-precision.
        // Both have to parse or the routine tab is blank.
        val json = """
            {"key":"gym","label":"Gym","starts_at":"14:00:00","ends_at":"15:30",
             "category_key":"gym","kind":"tracked","position":4}
        """.trimIndent()

        val slot = JarvisJson.decodeFromString(RoutineSlotDto.serializer(), json)

        assertEquals(LocalTime.of(14, 0), slot.startsAt)
        assertEquals(LocalTime.of(15, 30), slot.endsAt)
    }

    @Test
    fun `a routine decodes from the contract's own field names`() {
        val json = """
            {"routine_id":1,"name":"The week","version_id":4,"effective_from":"2026-09-01",
             "notes":["Reskill 20h/week"],
             "categories":[{"key":"reskill","label":"Reskill","cls":"committed"}],
             "days":[{"weekday":1,"starts_at":"07:30:00","ends_at":"01:00:00","note":"buffer day",
                      "slots":[{"key":"reskill","label":"Reskill","starts_at":"08:45:00",
                                "ends_at":"12:00:00","category_key":"reskill","kind":"tracked",
                                "position":1}]}]}
        """.trimIndent()

        val routine = JarvisJson.decodeFromString(RoutineDto.serializer(), json).toDomain()

        assertEquals(4L, routine.versionId)
        assertEquals(LocalDate.of(2026, 9, 1), routine.effectiveFrom)
        assertEquals(listOf("Reskill 20h/week"), routine.notes)
        assertEquals(DayOfWeek.MONDAY, routine.days.single().weekday)
        assertEquals("buffer day", routine.days.single().note)
        assertEquals("Reskill", routine.days.single().slots.single().label)
    }

    @Test
    fun `a start decodes from the contract's own field names`() {
        val json = """
            {"version_id":4,"slot_key":"gym","on":"2026-09-11",
             "started_at":"2026-09-11T04:20:00Z","clamped":true}
        """.trimIndent()

        val dto = JarvisJson.decodeFromString(SlotStartDto.serializer(), json)

        assertEquals(4L, dto.versionId)
        assertEquals("gym", dto.slotKey)
        assertEquals(LocalDate.of(2026, 9, 11), dto.on)
        assertEquals(Instant.parse("2026-09-11T04:20:00Z"), dto.startedAt)
        assertTrue(dto.clamped)
    }
}
