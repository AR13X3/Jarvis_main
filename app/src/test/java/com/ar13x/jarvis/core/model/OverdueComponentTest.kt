package com.ar13x.jarvis.core.model

import com.ar13x.jarvis.core.network.JarvisJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The overdue nudge on the wire, and the arithmetic of the allowance.
 *
 * Serialization first, per §8.3 — the polymorphic `components` array is where a
 * contract mismatch bites, and this type is being added to it before the
 * gateway sends one.
 */
class OverdueComponentTest {

    private fun decode(json: String): AgentComponent =
        JarvisJson.decodeFromString(AgentComponent.serializer(), json)

    @Test
    fun `it decodes from the agreed shape`() {
        val component = decode(
            """
            {"type":"overdue","occurrence_id":88,"task_id":12,
             "deadline":"2026-08-22T08:40:00Z",
             "extensions_used":1,"extensions_allowed":2}
            """.trimIndent(),
        )

        val overdue = component as AgentComponent.Overdue
        assertEquals(88L, overdue.occurrenceId)
        assertEquals(12L, overdue.taskId)
        assertEquals(1, overdue.extensionsUsed)
        assertEquals(1, overdue.extensionsLeft)
        assertTrue(overdue.canExtend)
    }

    @Test
    fun `it round-trips`() {
        val original = AgentComponent.Overdue(
            occurrenceId = 88,
            taskId = 12,
            extensionsUsed = 2,
            extensionsAllowed = 2,
            resolution = OverdueResolution.Lapsed,
        )
        val encoded = JarvisJson.encodeToString(AgentComponent.serializer(), original)
        assertEquals(original, decode(encoded))
        assertTrue("the discriminator must survive", encoded.contains("\"type\":\"overdue\""))
    }

    /** The cap is the feature. Exhausted means no extend buttons at all. */
    @Test
    fun `an exhausted allowance cannot be extended`() {
        val exhausted = AgentComponent.Overdue(
            occurrenceId = 1,
            taskId = 1,
            extensionsUsed = 2,
            extensionsAllowed = 2,
        )
        assertEquals(0, exhausted.extensionsLeft)
        assertFalse(exhausted.canExtend)
    }

    /** Answered cards stop offering buttons but stay in history (§5.3). */
    @Test
    fun `a resolved nudge offers nothing`() {
        val answered = AgentComponent.Overdue(
            occurrenceId = 1,
            taskId = 1,
            extensionsUsed = 0,
            extensionsAllowed = 2,
            resolution = OverdueResolution.Completed,
        )
        assertFalse(answered.canExtend)
    }

    /** A gateway sending more than we expect must never widen the allowance below zero. */
    @Test
    fun `over-spent counts clamp rather than go negative`() {
        val odd = AgentComponent.Overdue(
            occurrenceId = 1,
            taskId = 1,
            extensionsUsed = 5,
            extensionsAllowed = 2,
        )
        assertEquals(0, odd.extensionsLeft)
    }

    // --- the chips the server chooses ---------------------------------------

    /**
     * Read before the field exists, so the day the gateway starts sending it
     * every install in the field honours it without a release. That is the
     * mirror of §4.5: tolerate what we do not know, honour what we do.
     */
    @Test
    fun `offered minutes come from the server when sent`() {
        val component = decode(
            """
            {"type":"overdue","occurrence_id":1,"task_id":1,
             "extension_minutes":[10,20]}
            """.trimIndent(),
        ) as AgentComponent.Overdue

        assertEquals(listOf(10, 20), component.offeredMinutes)
    }

    /** A gateway that has not shipped the field yet behaves exactly as before. */
    @Test
    fun `absent means the default`() {
        val component = decode(
            """{"type":"overdue","occurrence_id":1,"task_id":1}""",
        ) as AgentComponent.Overdue

        assertEquals(listOf(15, 30, 60), component.offeredMinutes)
    }

    /**
     * These become buttons sized by weight, so the server can wreck the layout
     * with values it is otherwise entitled to send.
     */
    @Test
    fun `nonsense is sanitised rather than rendered`() {
        fun offered(vararg minutes: Int) = AgentComponent.Overdue(
            occurrenceId = 1,
            taskId = 1,
            extensionMinutes = minutes.toList(),
        ).offeredMinutes

        assertEquals("zero and negatives dropped", listOf(30), offered(0, -5, 30))
        assertEquals("duplicates collapsed, order fixed", listOf(15, 30), offered(30, 15, 30))
        assertEquals("capped at what fits", 4, offered(5, 10, 15, 20, 25, 30).size)
        assertEquals("empty falls back", listOf(15, 30, 60), offered())
        assertEquals("all-invalid falls back", listOf(15, 30, 60), offered(0, -1))
    }
}
