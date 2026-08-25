package com.ar13x.jarvis.reminders

import com.ar13x.jarvis.core.model.UpcomingOccurrence
import com.ar13x.jarvis.core.network.JarvisJson
import com.ar13x.jarvis.reminders.data.OccurrenceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The loop's state, carried as far as the notification.
 *
 * Without these fields on the mirror the notification cannot say how many
 * chances are left and the catch-up cannot know when to look — so the chain
 * stops after one unanswered question and the task lapses in silence.
 */
class NudgeMirrorTest {

    private fun decode(json: String) =
        JarvisJson.decodeFromString(UpcomingOccurrence.serializer(), json)

    private val minimal = """
        {"occurrence_id":88,"task_id":12,"title":"Charge my watch",
         "scheduled_for":"2026-08-26T13:00:00Z"}
    """.trimIndent()

    /**
     * A gateway predating the loop must still parse. The defaults describe a
     * task with its full allowance, which is what such an occurrence has.
     */
    @Test
    fun `an occurrence without the loop fields still parses`() {
        val occurrence = decode(minimal)
        assertEquals(0, occurrence.extensionsUsed)
        assertEquals(2, occurrence.extensionsAllowed)
        assertEquals(15, occurrence.graceMinutes)
        assertTrue(occurrence.canExtend)
    }

    @Test
    fun `the loop fields are read when sent`() {
        val occurrence = decode(
            """
            {"occurrence_id":88,"task_id":12,"title":"Charge my watch",
             "scheduled_for":"2026-08-26T13:00:00Z",
             "extensions_used":2,"extensions_allowed":2,
             "grace_minutes":20,"extension_minutes":[10,20]}
            """.trimIndent(),
        )
        assertEquals(20, occurrence.graceMinutes)
        assertEquals(listOf(10, 20), occurrence.extensionMinutes)
        assertEquals(0, occurrence.extensionsLeft)
        assertFalse("spent means no extend button", occurrence.canExtend)
    }

    /**
     * The chips are stored comma-separated rather than behind a type converter.
     * Cheap, but it has to survive the round trip or the notification offers the
     * wrong minutes.
     */
    @Test
    fun `offered minutes survive the string round trip`() {
        val entity = OccurrenceEntity(
            occurrenceId = 1,
            taskId = 1,
            title = "t",
            scheduledForMillis = 0,
            isPriority = false,
            extensionMinutes = "10,20,45",
        )
        assertEquals(listOf(10, 20, 45), entity.offeredMinutes)
    }

    /** A corrupt or empty column must not leave the notification with no buttons. */
    @Test
    fun `a broken minutes column falls back rather than emptying`() {
        fun offered(stored: String) = OccurrenceEntity(
            occurrenceId = 1,
            taskId = 1,
            title = "t",
            scheduledForMillis = 0,
            isPriority = false,
            extensionMinutes = stored,
        ).offeredMinutes

        assertEquals(listOf(15, 30, 60), offered(""))
        assertEquals(listOf(15, 30, 60), offered("nonsense"))
        assertEquals(listOf(15, 30, 60), offered("0,-5"))
        assertEquals(listOf(30), offered("nonsense,30"))
    }

    @Test
    fun `an exhausted occurrence offers no extension`() {
        val entity = OccurrenceEntity(
            occurrenceId = 1,
            taskId = 1,
            title = "t",
            scheduledForMillis = 0,
            isPriority = false,
            extensionsUsed = 2,
            extensionsAllowed = 2,
        )
        assertFalse(entity.canExtend)
        assertEquals(0, entity.extensionsLeft)
    }
}
