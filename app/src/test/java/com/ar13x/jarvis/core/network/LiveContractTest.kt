package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.model.Dashboard
import com.ar13x.jarvis.core.model.PagedSessions
import com.ar13x.jarvis.core.model.PagedTasks
import com.ar13x.jarvis.core.model.PagedTodos
import com.ar13x.jarvis.core.model.SectionsResponse
import com.ar13x.jarvis.core.model.UpcomingOccurrences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek

/**
 * Decodes **real gateway responses** with the app's own DTOs.
 *
 * Every other contract test in this repo is written from `openapi.json`, which
 * proves the DTOs match the *schema*. This proves they match what the server
 * actually sends — a different claim, and the one that was missing for the whole
 * of this work. A schema can be right while a response omits an "optional" field
 * the app treats as present, or spells an enum differently in practice.
 *
 * **It skips when the bodies are absent, which is almost always.** The responses
 * contain Joy's real tasks, sessions and routine, so they are deliberately *not*
 * committed — put them somewhere outside the repo and point this at them:
 *
 * ```
 * curl -H "Authorization: Bearer $TOKEN" \
 *   https://gw03.tail9662e3.ts.net/api/dashboard > <dir>/live-dashboard.json
 * ./gradlew test -Djarvis.live.dir=<dir>
 * ```
 *
 * A skipped test is honest here in a way a deleted one would not be: it records
 * that this check exists and that it has not run, rather than quietly leaving
 * the strongest available verification out of the suite.
 */
class LiveContractTest {

    private val dir: File? = System.getProperty("jarvis.live.dir")
        ?.let(::File)
        ?.takeIf { it.isDirectory }

    private fun body(name: String): String {
        val d = dir
        assumeTrue("no -Djarvis.live.dir with live bodies; skipping", d != null)
        val file = File(d, name)
        assumeTrue("missing " + name, file.isFile)
        return file.readText()
    }

    @Test
    fun `the real dashboard decodes, provenance and all`() {
        val d = JarvisJson.decodeFromString(Dashboard.serializer(), body("live-dashboard.json"))

        // The derivation the policy claims, checked against the real numbers
        // rather than against a fixture that agrees with itself by construction.
        assertTrue(d.policy.taskThresholdIsDerived)
        assertEquals(d.policy.graceMinutes, d.policy.routineDriftThresholdMinutes)
        assertEquals("due", d.period.bucketedBy)
    }

    @Test
    fun `the real routine decodes and its weekdays are one-based`() {
        val routine = JarvisJson
            .decodeFromString(RoutineDto.serializer(), body("live-routine.json"))

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), routine.days.map { it.weekday })

        val domain = routine.toDomain()
        assertEquals(DayOfWeek.MONDAY, domain.days.first().weekday)
        assertEquals(DayOfWeek.SUNDAY, domain.days.last().weekday)

        // Every slot's category resolves. A dangling `category_key` would render
        // as an uncoloured row rather than as an error, so it is worth asserting.
        val known = domain.categories.map { it.id }.toSet()
        assertTrue(domain.days.flatMap { it.slots }.all { it.categoryId in known })

        // Still not served (tracker 109), which is why `drifted` stays unknown.
        assertEquals(null, domain.driftToleranceMinutes)
    }

    @Test
    fun `real tasks, sections, occurrences, sessions and todos all decode`() {
        JarvisJson.decodeFromString(PagedTasks.serializer(), body("live-tasks.json"))
        JarvisJson.decodeFromString(SectionsResponse.serializer(), body("live-sections.json"))
        JarvisJson.decodeFromString(
            UpcomingOccurrences.serializer(),
            body("live-occ-upcoming.json"),
        )
        JarvisJson.decodeFromString(PagedSessions.serializer(), body("live-sessions.json"))
        JarvisJson.decodeFromString(PagedTodos.serializer(), body("live-todos.json"))
    }
}
