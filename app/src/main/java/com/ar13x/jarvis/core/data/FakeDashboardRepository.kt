package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.Dashboard
import com.ar13x.jarvis.core.model.DashboardPeriod
import com.ar13x.jarvis.core.model.DashboardPolicy
import com.ar13x.jarvis.core.model.DashboardProvenance
import com.ar13x.jarvis.core.model.DashboardTotals
import com.ar13x.jarvis.core.model.DriftingSlot
import com.ar13x.jarvis.core.model.DriftingTask
import com.ar13x.jarvis.core.model.FailingTask
import com.ar13x.jarvis.core.model.MissedSlot
import com.ar13x.jarvis.core.model.RoutineProvenance
import com.ar13x.jarvis.core.model.StaleTodo
import com.ar13x.jarvis.core.model.TodoStatus
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dashboard fixtures, for building the screen off the tailnet and for tests.
 *
 * [DashboardFixture.asItStandsToday] is not invented. It reproduces the state
 * gw03 reported live on 2026-09-01 — empty `missed`, empty `drifting_slots`,
 * `days_measured` 0, and 27 firings the event stream cannot speak for — because
 * **that is the state in which this screen is easiest to get wrong**. Every
 * section is empty and every one of them is empty for a different reason, none
 * of which is "everything is fine".
 */
object DashboardFixture {

    private val today: LocalDate = LocalDate.of(2026, 9, 1)

    /**
     * The thresholds as gw03 derives them. `drift_threshold_minutes` is
     * 15 + 2 × 60 = 135 — the longest the gateway ever said it would wait — and
     * the routine one is `GRACE_MINUTES` alone.
     */
    val policy = DashboardPolicy(
        driftThresholdMinutes = 135,
        driftMinFirings = 3,
        graceMinutes = 15,
        extensionsAllowed = 2,
        autoExtendMinutes = 60,
        routineDriftThresholdMinutes = 15,
        routineDriftMinStarts = 3,
    )

    /**
     * The live state on the day the dashboard's app half was started.
     *
     * Read it as four different silences:
     *  - `drifting` is empty because 27 firings predate the event stream, not
     *    because nothing drifts;
     *  - `missed` is empty because no routine day has been logged at all;
     *  - `drifting_slots` is empty for the same reason;
     *  - `stale` is empty because there are no undated to-dos yet — the only
     *    one of the four that means what it looks like.
     *
     * `failing` is the exception, and it is real: "Charge my watch" has lapsed
     * 9 times out of 12, which Joy already decided to live with (tracker 40).
     */
    val asItStandsToday = Dashboard(
        period = DashboardPeriod(dateFrom = today.minusDays(6), dateTo = today),
        totals = DashboardTotals(firings = 34, completed = 21, failed = 11, unresolved = 2),
        failing = listOf(
            FailingTask(
                taskId = 12,
                title = "Charge my watch",
                recurrenceText = "every day at midnight",
                firings = 12,
                failed = 9,
                completed = 3,
                lastFailedOn = today.minusDays(1),
                // False until gw03 ships the flag (tracker 108). The screen must
                // read correctly in both states, so the fixture leaves it off.
                accepted = false,
            ),
            FailingTask(
                taskId = 31,
                title = "Move the Saturday portions down",
                isPriority = true,
                recurrenceText = "every Thursday",
                firings = 4,
                failed = 2,
                completed = 2,
                lastFailedOn = today.minusDays(5),
            ),
        ),
        drifting = emptyList(),
        provenance = DashboardProvenance(
            observedFrom = Instant.parse("2026-09-01T05:20:00Z"),
            eventsObserved = 7,
            eventsReconstructed = 29,
            firingsWithoutHistory = 27,
        ),
        missed = emptyList(),
        driftingSlots = emptyList(),
        routineProvenance = RoutineProvenance(daysElapsed = 0, daysMeasured = 0),
        stale = emptyList(),
        policy = policy,
    )

    /**
     * The same screen once both domains can actually speak. Every section says
     * something, and no provenance banner is warranted.
     */
    val measured = asItStandsToday.copy(
        drifting = listOf(
            DriftingTask(
                taskId = 44,
                title = "Reskill standup notes",
                firingsMeasured = 6,
                medianSlipMinutes = 168,
                maxSlipMinutes = 240,
            ),
        ),
        provenance = DashboardProvenance(
            observedFrom = Instant.parse("2026-08-11T05:20:00Z"),
            eventsObserved = 96,
            eventsReconstructed = 0,
            firingsWithoutHistory = 0,
        ),
        missed = listOf(
            MissedSlot(
                slotKey = "gym",
                label = "Gym",
                categoryKey = "upkeep",
                daysMeasured = 5,
                missed = 3,
                lastMissedOn = LocalDate.of(2026, 8, 30),
            ),
        ),
        driftingSlots = listOf(
            DriftingSlot(
                slotKey = "reskill",
                label = "Reskill",
                categoryKey = "committed",
                startsMeasured = 5,
                medianDriftMinutes = 38,
                maxDriftMinutes = 71,
            ),
        ),
        routineProvenance = RoutineProvenance(
            daysElapsed = 7,
            daysMeasured = 5,
            loggingBeganOn = LocalDate.of(2026, 8, 26),
        ),
        stale = listOf(
            StaleTodo(
                todoId = 3,
                title = "Place the uni assessment weeks",
                tags = listOf("Uni"),
                status = TodoStatus.Open,
                updatedAt = Instant.parse("2026-08-14T02:11:00Z"),
                daysUntouched = 18,
            ),
        ),
    )
}

/**
 * The dashboard over fixtures, for working off the tailnet.
 *
 * Ignores the window entirely, and visibly so — a fake that pretended to filter
 * would be mistaken for a working feature, which is the same reason the routine
 * fake keeps nothing across process death.
 */
@Singleton
class FakeDashboardRepository @Inject constructor() : DashboardRepository {
    var response: Dashboard = DashboardFixture.asItStandsToday

    override suspend fun dashboard(from: LocalDate?, to: LocalDate?): Dashboard = response
}
