package com.ar13x.jarvis.reminders

import com.ar13x.jarvis.reminders.data.OccurrenceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window-replacement rule (plan §7.1), tested against a stand-in DAO.
 *
 * This is the rule gw03 got wrong on their side: a rescheduled task left its old
 * occurrence live, the window served both, and the device would have armed two
 * alarms for one reminder — with the client behaving perfectly on bad input.
 * The app's protection is that it keeps exactly what the last fetch said and
 * forgets everything else.
 */
class OccurrenceMirrorTest {

    /** Mimics `replaceWindow`'s upsert-then-delete-not-in semantics. */
    private class FakeWindow {
        val rows = linkedMapOf<Long, OccurrenceEntity>()
        fun replace(incoming: List<OccurrenceEntity>) {
            if (incoming.isEmpty()) { rows.clear(); return }
            incoming.forEach { rows[it.occurrenceId] = it }
            val keep = incoming.mapTo(mutableSetOf()) { it.occurrenceId }
            rows.keys.retainAll(keep)
        }
    }

    private fun occurrence(id: Long, task: Long, at: Long) =
        OccurrenceEntity(id, task, "Go to the gym", at, isPriority = false)

    @Test
    fun `an occurrence the server has dropped does not survive the refresh`() {
        val window = FakeWindow()
        window.replace(listOf(occurrence(1, 21, 1_000), occurrence(2, 22, 2_000)))

        // The server moved task 21, so occurrence 1 is replaced by 3.
        window.replace(listOf(occurrence(3, 21, 1_500), occurrence(2, 22, 2_000)))

        assertEquals(setOf(3L, 2L), window.rows.keys)
        assertTrue("the superseded occurrence must be gone", 1L !in window.rows)
    }

    @Test
    fun `a rescheduled task leaves exactly one occurrence, not two`() {
        val window = FakeWindow()
        window.replace(listOf(occurrence(1, 21, 1_000)))
        window.replace(listOf(occurrence(2, 21, 1_800)))

        val forTask = window.rows.values.filter { it.taskId == 21L }
        assertEquals("one alarm per task, not the old time and the new one", 1, forTask.size)
        assertEquals(1_800, forTask.single().scheduledForMillis)
    }

    @Test
    fun `an empty window clears the mirror rather than keeping stale rows`() {
        val window = FakeWindow()
        window.replace(listOf(occurrence(1, 21, 1_000)))
        window.replace(emptyList())
        assertTrue(window.rows.isEmpty())
    }
}
