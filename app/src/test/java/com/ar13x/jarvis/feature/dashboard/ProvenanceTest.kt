package com.ar13x.jarvis.feature.dashboard

import com.ar13x.jarvis.core.data.DashboardFixture
import com.ar13x.jarvis.core.model.DashboardPeriod
import com.ar13x.jarvis.core.model.DashboardProvenance
import com.ar13x.jarvis.core.model.FailingTask
import com.ar13x.jarvis.core.model.RoutineProvenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The four provenance rules of v2 plan §6, and the one thing they all guard
 * against: **an empty section rendering as good news.**
 *
 * These are the tests worth having on this screen. The layout can be judged by
 * looking at it; this cannot, because the failure is silent, it only lasts about
 * a week after each domain ships, and it errs in the direction that reassures —
 * so nobody reports it. Every assertion below is of the form "with nothing to
 * show, does the screen still refuse to say everything is fine".
 */
class ProvenanceTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 1)

    private fun view(dashboard: com.ar13x.jarvis.core.model.Dashboard = DashboardFixture.asItStandsToday) =
        DashboardView(dashboard, today)

    // --- task drift ------------------------------------------------------------

    @Test
    fun `an empty drift list with firings the stream cannot see is NOT a clean week`() {
        // The live state on 2026-09-01: drifting is empty and 27 firings predate
        // the event table. Rendering that as an empty state would have told Joy
        // nothing was drifting during the exact week nothing could be seen.
        val v = view()

        assertTrue(v.dashboard.drifting.isEmpty())
        assertFalse(v.taskDriftEvidence.isMeasured)
        assertEquals(
            "Not enough history yet — 27 firings predate the record.",
            (v.taskDriftEvidence as Evidence.Incomplete).note,
        )
    }

    @Test
    fun `an empty drift list with complete history IS a clean week`() {
        // The other half, and it has to work or the banner becomes permanent
        // furniture that nobody reads.
        val v = view(
            DashboardFixture.asItStandsToday.copy(
                provenance = DashboardProvenance(firingsWithoutHistory = 0),
            ),
        )

        assertTrue(v.taskDriftEvidence.isMeasured)
    }

    @Test
    fun `one unseen firing still suppresses the empty state`() {
        // No threshold. A section that only admitted doubt above some number
        // would teach the reader that its absence means zero.
        val v = view(
            DashboardFixture.asItStandsToday.copy(
                provenance = DashboardProvenance(firingsWithoutHistory = 1),
            ),
        )

        assertFalse(v.taskDriftEvidence.isMeasured)
        assertEquals(
            "Not enough history yet — 1 firing predates the record.",
            (v.taskDriftEvidence as Evidence.Incomplete).note,
        )
    }

    // --- routine ---------------------------------------------------------------

    @Test
    fun `nothing measured is not a perfect week`() {
        // The worst case of the four. Before anyone taps a slot, every tracked
        // slot of every finished day has no start, so `missed` is empty — and
        // that is indistinguishable from a week where nothing was missed.
        val v = view()

        assertTrue(v.dashboard.missed.isEmpty())
        assertTrue(v.dashboard.routineProvenance.measuredNothing)
        assertFalse(v.routineEvidence.isMeasured)
        assertTrue(
            (v.routineEvidence as Evidence.Incomplete).note.startsWith("Not measured yet"),
        )
    }

    @Test
    fun `a gap between days elapsed and days measured is always shown`() {
        // Two of seven days logged is a real result about those two days. It is
        // not a result about the week, and the screen has to say which.
        val v = view(
            DashboardFixture.measured.copy(
                routineProvenance = RoutineProvenance(daysElapsed = 7, daysMeasured = 6),
            ),
        )

        assertFalse(v.routineEvidence.isMeasured)
        assertEquals(
            "Measured on 6 of 7 days. Days with no logging at all are left out " +
                "rather than counted as missed.",
            (v.routineEvidence as Evidence.Incomplete).note,
        )
    }

    @Test
    fun `every day measured needs no caveat`() {
        val v = view(
            DashboardFixture.measured.copy(
                routineProvenance = RoutineProvenance(daysElapsed = 5, daysMeasured = 5),
            ),
        )

        assertTrue(v.routineEvidence.isMeasured)
    }

    @Test
    fun `a missed row always carries its denominator`() {
        // A bare "3" invites reading it against days_elapsed, which is a
        // different and larger number. MissedSlot.days_measured is on the wire
        // per row exactly so the row can say which denominator it means.
        val v = view()

        assertEquals("Missed 3 of 5 measured days", v.missedLine(3, 5))
        assertEquals("Missed 1 of 1 measured day", v.missedLine(1, 1))
    }

    // --- stale -----------------------------------------------------------------

    @Test
    fun `a historical window labels stale as still current`() {
        // `stale` ignores date_from/date_to on purpose, so in a historical
        // window it is the one section that keeps changing — which reads as a
        // bug unless it says so.
        val v = view(
            DashboardFixture.measured.copy(
                period = DashboardPeriod(
                    dateFrom = LocalDate.of(2026, 8, 1),
                    dateTo = LocalDate.of(2026, 8, 31),
                ),
            ),
        )

        assertEquals(
            "Always current — stale is measured from today, not from the window above.",
            v.staleCaption,
        )
    }

    @Test
    fun `a window ending today needs no stale caption`() {
        // The caption would be saying nothing, and a caveat that is always
        // present is one nobody reads when it matters.
        assertNull(view().staleCaption)
    }

    // --- accepted failures -----------------------------------------------------

    @Test
    fun `an accepted failure is sunk, never dropped`() {
        // Both halves matter. Leading with something Joy chose to live with
        // teaches her to skip the top row; removing it means the day it stops
        // failing looks identical to every day before it.
        val dashboard = DashboardFixture.asItStandsToday.let { d ->
            d.copy(failing = d.failing.map { if (it.taskId == 12L) it.copy(accepted = true) else it })
        }
        val v = view(dashboard)

        assertEquals(listOf(31L), v.failing.map { it.taskId })
        assertEquals(listOf(12L), v.acceptedFailures.map { it.taskId })

        // Still carrying its real numbers. Accepted is not the same as excused.
        val watch = v.acceptedFailures.single()
        assertEquals(9, watch.failed)
        assertEquals(12, watch.firings)
    }

    @Test
    fun `with no flag served, nothing is accepted and the list is unchanged`() {
        // gw03 has not shipped `accepted` yet (tracker 108). The default must
        // leave today's behaviour exactly as it is, or the screen changes
        // meaning on a deploy nobody coordinated.
        val v = view()

        assertTrue(v.acceptedFailures.isEmpty())
        assertEquals(v.dashboard.failing, v.failing)
    }

    // --- the shape of the response itself ---------------------------------------

    @Test
    fun `the task drift threshold is the derivation it claims to be`() {
        // grace + extensions * auto_extend = 15 + 2*60 = 135. Not used to
        // correct anything -- the gateway owns the number -- but a disagreement
        // should surface here rather than as a screen and a database quietly
        // meaning different things by "drifting".
        assertTrue(DashboardFixture.policy.taskThresholdIsDerived)
        assertEquals(135, DashboardFixture.policy.driftThresholdMinutes)
    }

    @Test
    fun `the routine drift threshold is grace alone, and matches the app's own tolerance`() {
        // The number SlotRow used to hardcode. Pinned here so that if either
        // side moves, this fails rather than the screen and the dashboard
        // disagreeing about the same slot.
        assertEquals(
            DashboardFixture.policy.graceMinutes,
            DashboardFixture.policy.routineDriftThresholdMinutes,
        )
        assertEquals(
            DashboardFixture.policy.routineDriftThresholdMinutes,
            com.ar13x.jarvis.core.data.RoutineFixture.theWeek.driftToleranceMinutes,
        )
    }

    @Test
    fun `a failing task is never silently deduplicated away`() {
        // Both rows survive the accepted split. An earlier draft filtered on
        // `accepted` in one place and forgot the other, which dropped a row.
        val dashboard = DashboardFixture.asItStandsToday.let { d ->
            d.copy(failing = d.failing.map { if (it.taskId == 12L) it.copy(accepted = true) else it })
        }
        val v = view(dashboard)

        assertEquals(
            dashboard.failing.size,
            v.failing.size + v.acceptedFailures.size,
        )
    }

    @Test
    fun `a failing task DTO parses without the accepted field`() {
        // Forward compatibility in the direction that actually matters: the
        // gateway serving today's shape must keep working.
        val json = """
            {"task_id":12,"title":"Charge my watch","firings":12,"failed":9,"completed":3}
        """.trimIndent()

        val parsed = com.ar13x.jarvis.core.network.JarvisJson
            .decodeFromString(FailingTask.serializer(), json)

        assertFalse(parsed.accepted)
        assertEquals(9, parsed.failed)
    }
}
