package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.model.Summaries
import com.ar13x.jarvis.core.model.Summary
import com.ar13x.jarvis.core.model.SummaryPeriod
import com.ar13x.jarvis.core.model.SummaryReminderVerb
import com.ar13x.jarvis.core.model.SummaryTodoVerb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Summaries (v2 plan §7), and **the exact thing that kept them unbuilt**.
 *
 * `SummaryFacts.reminders` and `.todos` are maps keyed by verb, and a key is
 * **absent rather than zero** when nothing of that kind happened. That makes a
 * misspelled lookup and a genuinely quiet day produce the same answer, and only
 * one of them is true — so the app was deliberately held back from parsing them
 * until gw03 published the legal keys (tracker 110).
 *
 * They are published now. This file reads them **out of the served contract**
 * rather than restating them, so it cannot agree with a mistake by repeating it.
 * `docs/gateway-openapi.json` is the deployed copy, sha `383d102ebc0df899`.
 */
class SummaryContractTest {

    private val contract: JsonObject = Json.parseToJsonElement(
        File("../docs/gateway-openapi.json")
            .takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?: File("docs/gateway-openapi.json").readText(Charsets.UTF_8),
    ).jsonObject

    private fun enumOf(schema: String): List<String> =
        contract["components"]!!.jsonObject["schemas"]!!.jsonObject[schema]!!
            .jsonObject["enum"]!!.jsonArray.map { it.toString().trim('"') }

    // --- the vocabulary this was blocked on ---------------------------------

    @Test
    fun `the reminder keys are spelled the way the contract spells them`() {
        assertEquals(
            enumOf("SummaryReminderVerb"),
            SummaryReminderVerb.entries.map { it.wire },
        )
    }

    @Test
    fun `the to-do keys are spelled the way the contract spells them`() {
        assertEquals(
            enumOf("SummaryTodoVerb"),
            SummaryTodoVerb.entries.map { it.wire },
        )
    }

    @Test
    fun `the summary key enums stay a strict subset of the full vocabularies`() {
        // gw03 published two tiers on purpose: the full domain vocabulary, and
        // the narrower set of keys a summary map can actually carry. Publishing
        // all eight reminder verbs as legal keys would have been honest about
        // the domain and misleading about the map — five unreachable branches,
        // and "no `extended` key" read as a fact about the day rather than about
        // the query. If the narrow set ever stops being a subset, one of them
        // has drifted.
        assertTrue(enumOf("SummaryReminderVerb").all { it in enumOf("ReminderVerb") })
        assertTrue(enumOf("SummaryTodoVerb").all { it in enumOf("TodoVerb") })
    }

    @Test
    fun `the period spellings match, in both directions`() {
        // `period` is an INLINE enum on `Summary`, not a named component like
        // the two verb vocabularies -- so it is read from the property rather
        // than from `components/schemas`. Worth doing rather than hardcoding
        // "daily"/"weekly": the app sends this string as a query parameter and
        // in the generate body, and a wrong one is a 422 at best and a silently
        // coerced default at worst.
        val published = contract["components"]!!.jsonObject["schemas"]!!.jsonObject["Summary"]!!
            .jsonObject["properties"]!!.jsonObject["period"]!!
            .jsonObject["enum"]!!.jsonArray.map { it.toString().trim('"') }

        assertEquals(listOf("daily", "weekly"), published)
        assertEquals(published, SummaryPeriod.entries.map { it.wireName() })
    }

    // --- decoding -----------------------------------------------------------

    @Test
    fun `a summary with prose and facts decodes, and keeps them apart`() {
        val summary = JarvisJson.decodeFromString(Summary.serializer(), FULL)

        assertEquals(SummaryPeriod.Daily, summary.period)
        assertEquals("gpt-oss-120b", summary.model)
        assertTrue(summary.text!!.startsWith("A steady day"))

        // The counted half, which is the trustworthy one.
        assertEquals(2, summary.facts.reminders(SummaryReminderVerb.Completed)?.count)
        assertEquals(
            listOf("Charge my watch", "Bins"),
            summary.facts.reminders(SummaryReminderVerb.Completed)?.titles,
        )
        assertEquals(1, summary.facts.todos(SummaryTodoVerb.Done)?.count)
    }

    @Test
    fun `an absent key is null, not zero`() {
        // The whole point. `lapsed` is simply not in the payload, and the screen
        // must be able to tell that from "lapsed: 0" — which is why the accessor
        // returns null rather than a SummaryCount(0).
        val summary = JarvisJson.decodeFromString(Summary.serializer(), FULL)

        assertNull(summary.facts.reminders(SummaryReminderVerb.Lapsed))
        assertNull(summary.facts.todos(SummaryTodoVerb.Cancelled))
    }

    @Test
    fun `a verb this build does not know is kept rather than dropped`() {
        // The map is keyed by String, not by the enum, precisely so an eleventh
        // verb does not throw and take the whole screen down — `coerceInputValues`
        // does not apply to map keys and there is no default to coerce to.
        val json = """
            {"summary_id":9,"period":"daily","period_start":"2026-09-01",
             "period_end":"2026-09-01","generated_at":"2026-09-02T20:00:00Z",
             "facts":{"period":"daily","period_start":"2026-09-01",
                      "period_end":"2026-09-01",
                      "reminders":{"completed":{"count":1},"snoozed":{"count":4}},
                      "anything_happened":true}}
        """.trimIndent()

        val summary = JarvisJson.decodeFromString(Summary.serializer(), json)

        assertEquals(1, summary.facts.reminders(SummaryReminderVerb.Completed)?.count)
        assertEquals(listOf("snoozed"), summary.facts.unknownReminderKeys)
        assertEquals(4, summary.facts.reminders.getValue("snoozed").count)
    }

    @Test
    fun `a facts-only summary decodes with no prose, which is not a failure`() {
        val json = """
            {"summary_id":3,"period":"weekly","period_start":"2026-08-24",
             "period_end":"2026-08-30","generated_at":"2026-08-31T20:00:00Z",
             "facts":{"period":"weekly","period_start":"2026-08-24",
                      "period_end":"2026-08-30","anything_happened":true}}
        """.trimIndent()

        val summary = JarvisJson.decodeFromString(Summary.serializer(), json)

        assertNull(summary.text)
        assertNull(summary.model)
        assertEquals(SummaryPeriod.Weekly, summary.period)
        assertTrue(summary.facts.anythingHappened)
    }

    @Test
    fun `provenance survives, because a floor is not a total`() {
        val json = """
            {"summary_id":4,"period":"daily","period_start":"2026-08-10",
             "period_end":"2026-08-10","generated_at":"2026-09-02T20:00:00Z",
             "facts":{"period":"daily","period_start":"2026-08-10",
                      "period_end":"2026-08-10",
                      "records_began_on":"2026-09-01",
                      "period_predates_records":true,
                      "anything_happened":false}}
        """.trimIndent()

        val summary = JarvisJson.decodeFromString(Summary.serializer(), json)

        assertTrue(summary.facts.periodPredatesRecords)
        assertEquals(java.time.LocalDate.of(2026, 9, 1), summary.facts.recordsBeganOn)
        // A period that predates the records is NOT the same as a quiet one, and
        // the screen has two different sentences for them.
        assertFalse(summary.facts.anythingHappened)
    }

    @Test
    fun `routine facts and the overrun flag decode`() {
        val summary = JarvisJson.decodeFromString(Summary.serializer(), FULL)
        val routine = summary.facts.routine!!

        assertEquals(3, routine.daysWithAnyLogging)
        val speedway = routine.slotsStarted.single { it.label.startsWith("Speedway") }
        assertEquals(2, speedway.times)
        // The overrun signal — a start recorded after its logical day ended.
        // Kept rather than smoothed away, so it has to survive the wire.
        assertTrue(speedway.anyAfterTheDayEnded)
    }

    @Test
    fun `a page of summaries decodes`() {
        val page = JarvisJson.decodeFromString(
            Summaries.serializer(),
            """{"summaries":[$FULL]}""",
        )

        assertEquals(1, page.summaries.size)
    }

    private companion object {
        val FULL = """
            {"summary_id":7,"period":"daily","tag":null,
             "period_start":"2026-09-01","period_end":"2026-09-01",
             "text":"A steady day: two reminders answered and one to-do closed.",
             "model":"gpt-oss-120b","generated_at":"2026-09-02T20:00:00Z",
             "facts":{"period":"daily","period_start":"2026-09-01",
                      "period_end":"2026-09-01","tag":null,
                      "reminders":{"completed":{"count":2,
                                   "titles":["Charge my watch","Bins"]}},
                      "todos":{"done":{"count":1,"titles":["FCM measurement"]}},
                      "routine":{"days_with_any_logging":3,
                                 "slots_started":[
                                   {"label":"Speedway shift","category":"speedway",
                                    "times":2,"any_after_the_day_ended":true},
                                   {"label":"Gym","category":"gym","times":1}]},
                      "records_began_on":"2026-09-01",
                      "period_predates_records":false,
                      "anything_happened":true}}
        """.trimIndent()
    }
}
