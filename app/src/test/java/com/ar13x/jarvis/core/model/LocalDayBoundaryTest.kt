package com.ar13x.jarvis.core.model

import com.ar13x.jarvis.core.network.JarvisJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * The highest-risk bug class in the whole system, and it is one assertion
 * (plan §8.3).
 *
 * The server runs UTC. A task the user sees as "8am Thursday" in Sydney is
 * stored as an instant on *Wednesday* in UTC. Anything that derives the display
 * day from the instant therefore puts the task on the wrong day — silently, and
 * only for part of each day, which is why it survives casual testing.
 *
 * The rule the app follows instead: `due_date` and `due_today` come from the
 * server and are never computed here (plan §3.2).
 */
class LocalDayBoundaryTest {

    private val sydney = ZoneId.of("Australia/Sydney")
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

    /**
     * 8am Thursday 22 Aug in Sydney is 22:00 Wednesday 21 Aug in UTC. The server
     * sends the local day it computed; the app displays that and nothing else.
     */
    private val json = """
        { "id": 12, "title": "Call the dentist", "description": "",
          "due_at": "2026-08-21T22:00:00Z",
          "due_date": "2026-08-22",
          "is_priority": false, "recurrence": null, "recurrence_text": null,
          "next_fire_at": null, "status": "active", "due_today": true,
          "created_at": "2026-08-19T02:00:00Z", "updated_at": "2026-08-19T02:00:00Z",
          "completed_at": null, "cancelled_at": null }
    """.trimIndent()

    @Test
    fun `the display day is the server's local day, not the instant's UTC day`() {
        val task = JarvisJson.decodeFromString(Task.serializer(), json)

        assertEquals(LocalDate.parse("2026-08-22"), task.dueDate)

        // The naive derivation — the one this codebase must never do — disagrees.
        // If this assertion ever starts failing, the fixture stopped covering the
        // boundary and the test has quietly become worthless.
        val derivedInUtc = task.dueAt.atZone(ZoneId.of("UTC")).toLocalDate()
        assertEquals(LocalDate.parse("2026-08-21"), derivedInUtc)
        assertNotEquals(task.dueDate, derivedInUtc)
    }

    @Test
    fun `due_today comes from the server field`() {
        val task = JarvisJson.decodeFromString(Task.serializer(), json)
        assertTrue(task.dueToday)
    }

    @Test
    fun `incomplete keeps its full mutation set and is not terminal`() {
        // Getting this wrong makes lapsed tasks unreschedulable, which is the
        // opposite of what they are for (plan §4.3).
        assertTrue(TaskStatus.Incomplete.allowsMutation)
        assertTrue(!TaskStatus.Incomplete.isTerminal)

        assertTrue(TaskStatus.Completed.isTerminal)
        assertTrue(TaskStatus.Cancelled.isTerminal)
        assertTrue(!TaskStatus.Active.isTerminal)
        assertTrue(!TaskStatus.Awaiting.isTerminal)
    }

    @Test
    fun `every status wire name round-trips`() {
        TaskStatus.entries.forEach { status ->
            val encoded = JarvisJson.encodeToString(TaskStatus.serializer(), status)
            assertEquals(status, JarvisJson.decodeFromString(TaskStatus.serializer(), encoded))
        }
        assertEquals(
            TaskStatus.Incomplete,
            JarvisJson.decodeFromString(TaskStatus.serializer(), "\"incomplete\""),
        )
    }
}
