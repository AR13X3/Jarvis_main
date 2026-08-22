package com.ar13x.jarvis.core.voice

import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.AgentResponse
import com.ar13x.jarvis.core.model.ProposalAction
import com.ar13x.jarvis.core.model.ProposalSummary
import com.ar13x.jarvis.core.model.TaskOption
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

/**
 * What gets read aloud.
 *
 * The stake here is the same one §4.5 names for the *visual* card: the parent
 * plan measured the model emitting a malformed day set roughly 1 in 5 on the
 * "last Friday of every month" pattern, and the only thing that catches it is a
 * human checking before confirming. Someone listening instead of looking has
 * only the utterance — so if the recurrence is not in it, the safety net is
 * gone for exactly the user who most needs it.
 */
class SpokenReplyTest {

    private lateinit var original: TimeZone

    @Before
    fun setUp() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Australia/Sydney"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(original)
    }

    private fun confirm(
        action: ProposalAction = ProposalAction.Create,
        title: String = "Call the dentist",
        dueAt: Instant? = Instant.parse("2026-08-21T09:00:00Z"),
        dueDate: LocalDate? = LocalDate.parse("2026-08-21"),
        recurrenceText: String? = null,
        isPriority: Boolean = false,
    ) = AgentComponent.Confirm(
        proposalId = "p1",
        summary = ProposalSummary(
            action = action,
            title = title,
            dueAt = dueAt,
            dueDate = dueDate,
            recurrenceText = recurrenceText,
            isPriority = isPriority,
        ),
    )

    @Test
    fun `the recurrence is spoken`() {
        val spoken = AgentResponse(
            text = "Just to confirm.",
            components = listOf(confirm(recurrenceText = "The last Friday of every month")),
        ).toUtterance()

        assertTrue(spoken, spoken.contains("The last Friday of every month"))
    }

    @Test
    fun `the title and the date are spoken`() {
        val spoken = AgentResponse(components = listOf(confirm())).toUtterance()

        assertTrue(spoken, spoken.contains("Call the dentist"))
        assertTrue(spoken, spoken.contains("Fri 21 Aug"))
    }

    /**
     * Nothing is written until confirmed (§3, guardrail 4). A listener has no
     * card in front of them, so the utterance has to say that out loud or the
     * feature quietly implies the task already exists.
     */
    @Test
    fun `it says the tap is still required`() {
        val spoken = AgentResponse(components = listOf(confirm())).toUtterance()

        assertTrue(spoken, spoken.lowercase().contains("tap confirm"))
    }

    @Test
    fun `prose is spoken without its markdown`() {
        val spoken = AgentResponse(text = "Moved to **Tuesday**, `9am`.").toUtterance()

        assertEquals("Moved to Tuesday, 9am.", spoken)
    }

    @Test
    fun `priority is mentioned only when set`() {
        assertTrue(
            AgentResponse(components = listOf(confirm(isPriority = true)))
                .toUtterance().contains("priority"),
        )
        assertFalse(
            AgentResponse(components = listOf(confirm(isPriority = false)))
                .toUtterance().contains("priority"),
        )
    }

    /**
     * Options are shown, not read. Three titles with dates is a list nobody can
     * hold in their head, and tapping one is the actual next step.
     */
    @Test
    fun `task options are not read out`() {
        val spoken = AgentResponse(
            text = "Which one?",
            components = listOf(
                AgentComponent.TaskOptions(
                    options = listOf(
                        TaskOption(taskId = 1, title = "Gym on Monday"),
                        TaskOption(taskId = 2, title = "Gym on Thursday"),
                    ),
                ),
            ),
        ).toUtterance()

        assertEquals("Which one?", spoken)
    }

    @Test
    fun `an empty response says nothing`() {
        assertEquals("", AgentResponse().toUtterance())
    }
}
