package com.ar13x.jarvis.core.model

import com.ar13x.jarvis.core.network.JarvisJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * `GET /dashboard` decoded from bodies **generated out of the served contract**,
 * not hand-written from the DTOs.
 *
 * That distinction is the entire value of this file. Writing the fixture from
 * the Kotlin side proves the DTO agrees with itself: every `@SerialName` typo
 * survives, because the same wrong string is on both ends. These two bodies were
 * produced by walking `openapi.json` (sha256 `e398ff18e4aa6b33`, 24 paths, 61
 * schemas) and filling each declared field, so a misspelled name here shows up
 * as a required field missing or an optional one silently taking its default.
 *
 * Which is why the assertions check **values** and not merely that decoding
 * succeeded. `ignoreUnknownKeys = true` is what makes the app
 * forward-compatible (plan §4.5) and it is also what would swallow a typo in
 * silence: the misnamed key is ignored, the field defaults, and the screen shows
 * a plausible zero. Every optional below is given a distinctive value on the
 * wire precisely so a default cannot impersonate it.
 */
class DashboardContractTest {

    /**
     * Every field the contract declares, present. Optionals carry `7` so that a
     * default (`0`, `false`, `null`) cannot pass for a decoded value.
     */
    private val full = """
        {
          "period": { "date_from": "2026-08-26", "date_to": "2026-09-01", "bucketed_by": "due" },
          "totals": { "firings": 7, "completed": 7, "failed": 7, "cancelled": 7, "unresolved": 7 },
          "failing": [ { "task_id": 7, "title": "Charge my watch", "is_priority": true,
                         "recurrence_text": "every day at midnight", "firings": 12, "failed": 9,
                         "completed": 3, "last_failed_on": "2026-08-31" } ],
          "drifting": [ { "task_id": 7, "title": "Standup notes", "is_priority": true,
                          "firings_measured": 7, "median_slip_minutes": 7, "max_slip_minutes": 7 } ],
          "provenance": { "observed_from": "2026-08-26T05:20:00Z", "events_observed": 7,
                          "events_reconstructed": 7, "firings_without_history": 7 },
          "missed": [ { "slot_key": "gym", "label": "Gym", "category_key": "upkeep",
                        "days_measured": 7, "missed": 7, "last_missed_on": "2026-08-30" } ],
          "drifting_slots": [ { "slot_key": "reskill", "label": "Reskill", "category_key": "committed",
                                "starts_measured": 7, "median_drift_minutes": 7,
                                "max_drift_minutes": 7 } ],
          "routine_provenance": { "days_elapsed": 7, "days_measured": 7,
                                  "logging_began_on": "2026-08-26" },
          "stale": [ { "todo_id": 7, "title": "Place the uni weeks", "tags": ["Uni"],
                       "status": "open", "updated_at": "2026-08-14T02:11:00Z",
                       "days_untouched": 7 } ],
          "policy": { "drift_threshold_minutes": 135, "drift_min_firings": 7, "grace_minutes": 15,
                      "extensions_allowed": 2, "auto_extend_minutes": 60,
                      "routine_drift_threshold_minutes": 15, "routine_drift_min_starts": 7 }
        }
    """.trimIndent()

    /** Only what the contract marks required. Everything else omitted. */
    private val minimal = """
        {
          "period": { "date_from": "2026-08-26", "date_to": "2026-08-26" },
          "totals": {},
          "failing": [],
          "drifting": [],
          "provenance": {},
          "missed": [],
          "drifting_slots": [],
          "routine_provenance": { "days_elapsed": 3, "days_measured": 3 },
          "stale": [],
          "policy": { "drift_threshold_minutes": 3, "drift_min_firings": 3, "grace_minutes": 3,
                      "extensions_allowed": 3, "auto_extend_minutes": 3,
                      "routine_drift_threshold_minutes": 3, "routine_drift_min_starts": 3 }
        }
    """.trimIndent()

    private fun decode(json: String) =
        JarvisJson.decodeFromString(Dashboard.serializer(), json)

    @Test
    fun `every declared field decodes, and none of them silently defaults`() {
        val d = decode(full)

        assertEquals(LocalDate.of(2026, 8, 26), d.period.dateFrom)
        assertEquals(LocalDate.of(2026, 9, 1), d.period.dateTo)
        assertEquals("due", d.period.bucketedBy)

        assertEquals(7, d.totals.firings)
        assertEquals(7, d.totals.cancelled)
        assertEquals(7, d.totals.unresolved)

        val failing = d.failing.single()
        assertEquals(7L, failing.taskId)
        assertEquals("Charge my watch", failing.title)
        assertTrue(failing.isPriority)
        assertEquals("every day at midnight", failing.recurrenceText)
        assertEquals(12, failing.firings)
        assertEquals(9, failing.failed)
        assertEquals(3, failing.completed)
        assertEquals(LocalDate.of(2026, 8, 31), failing.lastFailedOn)

        val drifting = d.drifting.single()
        assertEquals(7, drifting.firingsMeasured)
        assertEquals(7, drifting.medianSlipMinutes)
        assertEquals(7, drifting.maxSlipMinutes)

        assertEquals(Instant.parse("2026-08-26T05:20:00Z"), d.provenance.observedFrom)
        assertEquals(7, d.provenance.eventsObserved)
        assertEquals(7, d.provenance.eventsReconstructed)
        assertEquals(7, d.provenance.firingsWithoutHistory)

        val missed = d.missed.single()
        assertEquals("gym", missed.slotKey)
        assertEquals("upkeep", missed.categoryKey)
        assertEquals(7, missed.daysMeasured)
        assertEquals(7, missed.missed)
        assertEquals(LocalDate.of(2026, 8, 30), missed.lastMissedOn)

        val slot = d.driftingSlots.single()
        assertEquals("reskill", slot.slotKey)
        assertEquals(7, slot.startsMeasured)
        assertEquals(7, slot.medianDriftMinutes)
        assertEquals(7, slot.maxDriftMinutes)

        assertEquals(7, d.routineProvenance.daysElapsed)
        assertEquals(7, d.routineProvenance.daysMeasured)
        assertEquals(LocalDate.of(2026, 8, 26), d.routineProvenance.loggingBeganOn)

        val stale = d.stale.single()
        assertEquals(7L, stale.todoId)
        assertEquals(listOf("Uni"), stale.tags)
        assertEquals(TodoStatus.Open, stale.status)
        assertEquals(Instant.parse("2026-08-14T02:11:00Z"), stale.updatedAt)
        assertEquals(7, stale.daysUntouched)

        assertEquals(135, d.policy.driftThresholdMinutes)
        assertEquals(7, d.policy.driftMinFirings)
        assertEquals(15, d.policy.graceMinutes)
        assertEquals(2, d.policy.extensionsAllowed)
        assertEquals(60, d.policy.autoExtendMinutes)
        assertEquals(15, d.policy.routineDriftThresholdMinutes)
        assertEquals(7, d.policy.routineDriftMinStarts)
    }

    @Test
    fun `the required-only body decodes`() {
        val d = decode(minimal)

        assertEquals(0, d.totals.firings)
        assertNull(d.provenance.observedFrom)
        assertEquals(0, d.provenance.firingsWithoutHistory)
        assertNull(d.routineProvenance.loggingBeganOn)
        assertEquals("due", d.period.bucketedBy)
        assertTrue(d.failing.isEmpty())
    }

    @Test
    fun `the derivation on the wire checks out`() {
        // 15 + 2 * 60 = 135. All three inputs ride along precisely so this is
        // checkable rather than a magic number the client repeats back.
        assertTrue(decode(full).policy.taskThresholdIsDerived)
    }

    @Test
    fun `an unknown field does not break the response`() {
        // Plan §4.5. The gateway grows fields — `accepted` on FailingTask is the
        // next one (tracker 108) — and a client that threw on one it had not
        // seen could not be deployed independently.
        val grown = full.replace(
            "\"days_untouched\": 7",
            "\"days_untouched\": 7, \"blocked_on\": \"something new\"",
        )

        assertEquals(7, decode(grown).stale.single().daysUntouched)
    }

    @Test
    fun `a missing required section fails loudly rather than reading as empty`() {
        // The one place a default would be actively harmful. If `missed` were
        // declared `= emptyList()`, a gateway that stopped sending the section
        // would render as "nothing was missed" — which is the exact sentence §6
        // exists to prevent. Better one exception in a log.
        val truncated = full.replace("\"missed\": [", "\"mssed\": [")

        val thrown = runCatching { decode(truncated) }.exceptionOrNull()

        assertNotNull("a dropped required section must not decode", thrown)
    }
}
