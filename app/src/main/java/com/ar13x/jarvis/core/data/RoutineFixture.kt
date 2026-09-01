package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.CategoryClass
import com.ar13x.jarvis.core.model.Routine
import com.ar13x.jarvis.core.model.RoutineCategory
import com.ar13x.jarvis.core.model.RoutineDay
import com.ar13x.jarvis.core.model.RoutineSlot
import com.ar13x.jarvis.core.model.SlotKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Joy's actual week, transcribed from `docs/the-week.html`.
 *
 * Not invented sample data. This is the real routine the feature exists to
 * serve, which means the day view is built against the shape it will actually
 * meet — Thursday's late start, three consecutive Speedway nights running past
 * midnight, and a Wednesday buffer that must be left alone.
 *
 * `RoutineFixtureTest` checks the seven weekly category totals against the
 * numbers stated in the source file. That is what makes this a transcription
 * rather than an approximation: a mistyped time changes a total and the test
 * says which one.
 *
 * **The slot kinds have had their pass** (v2 plan §9) — gw03 counted them
 * against this file and the source, and Joy settled the one that was genuinely
 * a judgement call rather than a countable fact.
 *
 * Two are worth knowing. **Batch cook is Tracked** although it sits in the Life
 * category with the scaffold: it is a real commitment that the rest of the
 * week's eating depends on. And **Calls is Free**, not Tracked — Joy's own
 * category is called "Free, buffer, calls", which is Joy classifying it, and an
 * unmade call at 11pm on a Tuesday is not a failure.
 *
 * **Buffer is only ever Wednesday's two slots.** Buffer means the success
 * condition inverts, and the footer names exactly those two: "Wednesday's
 * 5:30–8:30 and 11–1 are buffer." It was on fourteen before they were counted.
 *
 * Kind does not derive from category and must not be made to. This file has to
 * break that rule fourteen times to be right — twice for the cooks, twelve
 * times for Free.
 */
object RoutineFixture {

    val categories = listOf(
        RoutineCategory("speedway", "Speedway", CategoryClass.Committed),
        RoutineCategory("reskill", "Reskill / CBAI", CategoryClass.Committed),
        RoutineCategory("uni", "Uni", CategoryClass.Committed),
        RoutineCategory("webdev", "Web dev", CategoryClass.Committed),
        RoutineCategory("gym", "Gym", CategoryClass.Committed),
        RoutineCategory("life", "Life upkeep", CategoryClass.Upkeep),
        RoutineCategory("free", "Free, buffer, calls", CategoryClass.Free),
    )

    val theWeek: Routine = Routine(
        routineId = 1,
        // A fixture value. The gateway issues real version ids on
        // `Routine.version_id`; this stands in until the app binds the remote
        // repository, and it exists so a start recorded here still carries a
        // version rather than a null.
        versionId = 1,
        name = "The week",
        effectiveFrom = LocalDate.of(2026, 9, 1),
        categories = categories,
        days = listOf(monday(), tuesday(), wednesday(), thursday(), friday(), saturday(), sunday()),
        notes = listOf(
            "Reskill 20h/week. Meetings 9–11pm Mon–Thu; Mon/Tue/Wed add a 3h day block, " +
                "Thursday adds 1h, Friday is 2h in the morning only.",
            "Thursday is the day off — wake at 10 to pay down sleep debt, football from 12:30. " +
                "The 9pm meeting is the one thing that intrudes.",
            "Wednesday's 5:30–8:30 and 11–1 are buffer. Spend them on overruns, not new work.",
            "Speedway nights give about 6 hours' sleep, three in a row. That is the hardest stretch.",
            "Two cooks: Sunday 8:30–10:30 covers Mon to Wed, Thursday 5–6:30 covers Thu to Sun. " +
                "Freeze the Sat and Sun portions on Thursday and move them down the night before.",
            "Not yet placed: uni assessment weeks, 10–15h. Wednesday's buffer is the only place " +
                "it can come from.",
        ),
        /**
         * A fixture value, standing in for the gateway's
         * `routine_drift_threshold_minutes` until it rides on `GET /routine`
         * (tracker 109). It matches what the gateway derives today — `GRACE_MINUTES`,
         * which is 15 — so the day view behaves exactly as it did before the
         * constant was removed from `SlotRow`.
         *
         * Changing this number changes a fixture. It no longer changes what the
         * app believes about a live routine, which is the whole point of moving it.
         */
        driftToleranceMinutes = 15,
    )

    // --- days -----------------------------------------------------------------

    private fun monday() = RoutineDay(
        weekday = DayOfWeek.MONDAY,
        startsAt = LocalTime.parse("08:00"),
        endsAt = LocalTime.parse("01:00"),
        slots = weekdayMorning("mon") + listOf(
            slot("mon-uni", "16:30", "20:00", "Uni, including travel", "uni", SlotKind.Tracked),
            slot("mon-dinner", "20:00", "20:30", "Dinner", "life", SlotKind.Scaffold),
            slot("mon-breather", "20:30", "21:00", "Breather", "free", SlotKind.Free),
            slot("mon-meet", "21:00", "23:00", "Reskill — meetings, daily report", "reskill", SlotKind.Tracked),
            slot("mon-free", "23:00", "01:00", "Free", "free", SlotKind.Free),
        ),
    )

    private fun tuesday() = RoutineDay(
        weekday = DayOfWeek.TUESDAY,
        startsAt = LocalTime.parse("08:00"),
        endsAt = LocalTime.parse("01:00"),
        slots = weekdayMorning("tue") + listOf(
            slot("tue-uni", "16:30", "20:00", "Uni, including travel", "uni", SlotKind.Tracked),
            slot("tue-dinner", "20:00", "20:30", "Dinner", "life", SlotKind.Scaffold),
            slot("tue-breather", "20:30", "21:00", "Breather", "free", SlotKind.Free),
            slot("tue-meet", "21:00", "23:00", "Reskill — meetings, daily report", "reskill", SlotKind.Tracked),
            slot("tue-calls", "23:00", "01:00", "Calls", "free", SlotKind.Free),
        ),
    )

    /** Monday and Tuesday are identical until uni; only the last slot differs. */
    private fun weekdayMorning(prefix: String) = listOf(
        slot("$prefix-wake", "08:00", "08:45", "Wake, shower, breakfast", "life", SlotKind.Scaffold),
        slot("$prefix-reskill", "08:45", "11:45", "Reskill / CBAI — block 1", "reskill", SlotKind.Tracked),
        slot("$prefix-lunch", "11:45", "12:15", "Lunch", "life", SlotKind.Scaffold),
        slot("$prefix-webdev", "12:15", "14:15", "Web dev", "webdev", SlotKind.Tracked),
        slot("$prefix-gym", "14:15", "15:45", "Gym", "gym", SlotKind.Tracked),
        slot("$prefix-shower", "15:45", "16:15", "Shower, snack", "life", SlotKind.Scaffold),
        slot("$prefix-ready", "16:15", "16:30", "Get ready to leave", "life", SlotKind.Scaffold),
    )

    private fun wednesday() = RoutineDay(
        weekday = DayOfWeek.WEDNESDAY,
        startsAt = LocalTime.parse("08:00"),
        endsAt = LocalTime.parse("01:00"),
        note = "buffer day",
        slots = listOf(
            slot("wed-wake", "08:00", "08:45", "Wake, shower, breakfast", "life", SlotKind.Scaffold),
            slot("wed-setup", "08:45", "09:00", "Set up for class", "life", SlotKind.Scaffold),
            slot("wed-uni", "09:00", "12:00", "Uni — online", "uni", SlotKind.Tracked),
            slot("wed-lunch", "12:00", "12:30", "Lunch", "life", SlotKind.Scaffold),
            slot("wed-reskill", "12:30", "15:30", "Reskill / CBAI — block 1", "reskill", SlotKind.Tracked),
            slot("wed-gym", "15:30", "17:00", "Gym", "gym", SlotKind.Tracked),
            slot("wed-shower", "17:00", "17:30", "Shower, snack", "life", SlotKind.Scaffold),
            slot("wed-buffer", "17:30", "20:30", "Buffer — keep empty", "free", SlotKind.Buffer),
            slot("wed-dinner", "20:30", "21:00", "Dinner", "life", SlotKind.Scaffold),
            slot("wed-meet", "21:00", "23:00", "Reskill — meetings, daily report", "reskill", SlotKind.Tracked),
            slot("wed-free", "23:00", "01:00", "Buffer, free", "free", SlotKind.Buffer),
        ),
    )

    private fun thursday() = RoutineDay(
        weekday = DayOfWeek.THURSDAY,
        startsAt = LocalTime.parse("10:00"),
        endsAt = LocalTime.parse("01:00"),
        note = "day off",
        slots = listOf(
            slot("thu-wake", "10:00", "11:00", "Sleep in, wake slow, breakfast", "life", SlotKind.Scaffold),
            slot("thu-reskill", "11:00", "12:00", "Reskill — catch-up, prep for tonight", "reskill", SlotKind.Tracked),
            slot("thu-lunch", "12:00", "12:30", "Lunch", "life", SlotKind.Scaffold),
            slot("thu-off", "12:30", "17:00", "Football, drawing, friends", "free", SlotKind.Free),
            slot("thu-cook", "17:00", "18:30", "Batch cook — covers Thu to Sun", "life", SlotKind.Tracked),
            slot("thu-dinner", "18:30", "19:30", "Dinner, shower", "life", SlotKind.Scaffold),
            slot("thu-free", "19:30", "21:00", "Free", "free", SlotKind.Free),
            slot("thu-meet", "21:00", "23:00", "Reskill — meetings, daily report", "reskill", SlotKind.Tracked),
            slot("thu-calls", "23:00", "01:00", "Calls", "free", SlotKind.Free),
        ),
    )

    private fun friday() = RoutineDay(
        weekday = DayOfWeek.FRIDAY,
        startsAt = LocalTime.parse("08:00"),
        endsAt = LocalTime.parse("02:00"),
        slots = listOf(
            slot("fri-wake", "08:00", "08:45", "Wake, shower, breakfast", "life", SlotKind.Scaffold),
            slot("fri-reskill", "08:45", "10:45", "Reskill / CBAI — morning block", "reskill", SlotKind.Tracked),
            slot("fri-webdev", "10:45", "11:45", "Web dev", "webdev", SlotKind.Tracked),
            slot("fri-lunch", "11:45", "12:30", "Lunch", "life", SlotKind.Scaffold),
            slot("fri-free", "12:30", "13:45", "Free", "free", SlotKind.Free),
            slot("fri-speedway", "13:45", "00:15", "Speedway — leave 1:45, shift 2:30–12:00", "speedway", SlotKind.Tracked),
            slot("fri-late", "00:15", "01:00", "Shower, late food", "life", SlotKind.Scaffold),
            slot("fri-wind", "01:00", "02:00", "Wind-down", "free", SlotKind.Free),
        ),
    )

    private fun saturday() = RoutineDay(
        weekday = DayOfWeek.SATURDAY,
        startsAt = LocalTime.parse("08:00"),
        endsAt = LocalTime.parse("02:00"),
        slots = listOf(
            slot("sat-wake", "08:00", "08:30", "Wake, breakfast", "life", SlotKind.Scaffold),
            slot("sat-gym", "08:30", "10:00", "Gym", "gym", SlotKind.Tracked),
            slot("sat-webdev", "10:00", "12:00", "Web dev", "webdev", SlotKind.Tracked),
            slot("sat-lunch", "12:00", "13:00", "Lunch", "life", SlotKind.Scaffold),
            slot("sat-free", "13:00", "13:45", "Free", "free", SlotKind.Free),
            slot("sat-speedway", "13:45", "00:15", "Speedway — leave 1:45, shift 2:30–12:00", "speedway", SlotKind.Tracked),
            slot("sat-late", "00:15", "01:00", "Shower, late food", "life", SlotKind.Scaffold),
            slot("sat-wind", "01:00", "02:00", "Wind-down", "free", SlotKind.Free),
        ),
    )

    private fun sunday() = RoutineDay(
        weekday = DayOfWeek.SUNDAY,
        startsAt = LocalTime.parse("08:00"),
        endsAt = LocalTime.parse("02:00"),
        slots = listOf(
            slot("sun-wake", "08:00", "08:30", "Wake, breakfast", "life", SlotKind.Scaffold),
            slot("sun-cook", "08:30", "10:30", "Batch cook — big batch, covers Mon to Wed", "life", SlotKind.Tracked),
            slot("sun-webdev", "10:30", "12:30", "Web dev", "webdev", SlotKind.Tracked),
            slot("sun-lunch", "12:30", "13:45", "Lunch, get ready", "life", SlotKind.Scaffold),
            slot("sun-speedway", "13:45", "00:15", "Speedway — leave 1:45, shift 2:30–12:00", "speedway", SlotKind.Tracked),
            slot("sun-late", "00:15", "00:45", "Shower, late food", "life", SlotKind.Scaffold),
            slot("sun-wind", "00:45", "02:00", "Wind-down", "free", SlotKind.Free),
        ),
    )

    private fun slot(
        id: String,
        start: String,
        end: String,
        label: String,
        categoryId: String,
        kind: SlotKind,
    ) = RoutineSlot(
        id = id,
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        label = label,
        categoryId = categoryId,
        kind = kind,
    )
}
