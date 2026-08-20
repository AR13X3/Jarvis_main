package com.ar13x.jarvis.core.model

import com.ar13x.jarvis.core.network.JarvisJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Serialization tests first (plan §8.3): the polymorphic `components` array is
 * where a contract mismatch between app and gateway will bite, and it will bite
 * silently — a wrong shape does not crash the build, it blanks a message.
 */
class AgentComponentSerializationTest {

    @Test
    fun `parses the confirm component from the contract example`() {
        val json = """
            {
              "text": "Just to confirm — 'Call the dentist', Thursday 21 Aug, 9:00 AM.",
              "components": [
                { "type": "confirm",
                  "proposal_id": "9f2c",
                  "summary": { "action": "create", "title": "Call the dentist",
                               "due_at": "2026-08-21T09:00:00Z", "due_date": "2026-08-21",
                               "is_priority": false, "recurrence_text": null,
                               "description": "" } }
              ]
            }
        """.trimIndent()

        val response = JarvisJson.decodeFromString(AgentResponse.serializer(), json)
        val confirm = response.components.single() as AgentComponent.Confirm

        assertEquals("9f2c", confirm.proposalId)
        assertEquals(ProposalAction.Create, confirm.summary.action)
        assertEquals("Call the dentist", confirm.summary.title)
        assertEquals(Instant.parse("2026-08-21T09:00:00Z"), confirm.summary.dueAt)
        assertEquals("2026-08-21", confirm.summary.dueDate.toString())
        assertNull(confirm.summary.recurrenceText)
        // A gateway that omits `status` still parses, and the card reads as live.
        assertEquals(ProposalStatus.Pending, confirm.status)
    }

    @Test
    fun `parses task_options and its cursor`() {
        val json = """
            { "text": "",
              "components": [
                { "type": "task_options",
                  "options": [ { "task_id": 12, "title": "Dinner with Sam",
                                 "due_at": "2026-08-21T09:00:00Z" } ],
                  "more_cursor": "eyJvZmZzZXQiOjN9" } ] }
        """.trimIndent()

        val options = JarvisJson.decodeFromString(AgentResponse.serializer(), json)
            .components.single() as AgentComponent.TaskOptions

        assertEquals(1, options.options.size)
        assertEquals(12L, options.options.single().taskId)
        assertEquals("eyJvZmZzZXQiOjN9", options.moreCursor)
    }

    /**
     * The forward-compatibility case, and the reason the serializer is
     * hand-written. The gateway will ship component types this build has never
     * heard of; an app that threw on one could not render the rest of the
     * message, let alone the rest of the conversation.
     */
    @Test
    fun `an unknown component type does not throw and keeps its text`() {
        val json = """
            { "text": "Here is the receipt.",
              "components": [
                { "type": "attachment_preview", "text": "receipt.jpg",
                  "attachment_id": "abc", "width": 800 },
                { "type": "confirm", "proposal_id": "p1",
                  "summary": { "action": "complete", "title": "Gym" } } ] }
        """.trimIndent()

        val response = JarvisJson.decodeFromString(AgentResponse.serializer(), json)

        assertEquals(2, response.components.size)
        val unknown = response.components[0] as AgentComponent.Unknown
        assertEquals("attachment_preview", unknown.type)
        assertEquals("receipt.jpg", unknown.text)
        // The known component alongside it still parses — one unrecognised type
        // must not poison the whole array.
        assertTrue(response.components[1] is AgentComponent.Confirm)
    }

    @Test
    fun `a component with no type at all is tolerated`() {
        val response = JarvisJson.decodeFromString(
            AgentResponse.serializer(),
            """{ "text": "", "components": [ { "text": "orphan" } ] }""",
        )
        val unknown = response.components.single() as AgentComponent.Unknown
        assertNull(unknown.type)
        assertEquals("orphan", unknown.text)
    }

    /**
     * The handoff out of general chat (BUILD_NOTES §3.11). Until the gateway
     * ships it this parses nothing real — but it is the shape the app is built
     * against, so it is the shape the contract test asserts.
     */
    @Test
    fun `parses the new_task handoff component`() {
        val json = """
            { "text": "I can't create tasks here, but I can set this one up.",
              "components": [
                { "type": "new_task",
                  "label": "Set this up",
                  "seed": "remind me to go to the gym tomorrow at 11pm" } ] }
        """.trimIndent()

        val handoff = JarvisJson.decodeFromString(AgentResponse.serializer(), json)
            .components.single() as AgentComponent.NewTask

        assertEquals("Set this up", handoff.label)
        assertEquals("remind me to go to the gym tomorrow at 11pm", handoff.seed)
    }

    @Test
    fun `a new_task without a label falls back rather than failing`() {
        val handoff = JarvisJson.decodeFromString(
            AgentResponse.serializer(),
            """{ "text": "", "components": [ { "type": "new_task", "seed": "walk the dog" } ] }""",
        ).components.single() as AgentComponent.NewTask

        assertEquals("Set this up", handoff.label)
        assertEquals("walk the dog", handoff.seed)
    }

    @Test
    fun `every component type round-trips`() {
        val original = AgentResponse(
            text = "hello",
            components = listOf(
                AgentComponent.Confirm(
                    proposalId = "p1",
                    summary = ProposalSummary(
                        action = ProposalAction.Update,
                        title = "Weekly review",
                        dueAt = Instant.parse("2026-08-21T09:00:00Z"),
                        dueDate = java.time.LocalDate.parse("2026-08-21"),
                        isPriority = true,
                        recurrenceText = "Every Friday",
                    ),
                    status = ProposalStatus.Confirmed,
                ),
                AgentComponent.TaskOptions(
                    options = listOf(TaskOption(7, "Dinner with Sam", Instant.parse("2026-08-21T09:00:00Z"))),
                    moreCursor = "cursor",
                ),
            ),
        )

        val encoded = JarvisJson.encodeToString(AgentResponse.serializer(), original)
        val decoded = JarvisJson.decodeFromString(AgentResponse.serializer(), encoded)

        assertEquals(original, decoded)
        // The discriminator survives encoding — without it the gateway could not
        // read back what the app sent.
        val types = JarvisJson.parseToJsonElement(encoded)
            .jsonObject["components"]!!
            .let { it as kotlinx.serialization.json.JsonArray }
            .map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(listOf("confirm", "task_options"), types)
    }

    @Test
    fun `an unknown component round-trips losslessly`() {
        val json = """{"text":"","components":[{"type":"future_thing","text":"x","extra":[1,2]}]}"""

        val once = JarvisJson.decodeFromString(AgentResponse.serializer(), json)
        val encoded = JarvisJson.encodeToString(AgentResponse.serializer(), once)
        val twice = JarvisJson.decodeFromString(AgentResponse.serializer(), encoded)

        assertEquals(once, twice)
        val unknown = twice.components.single() as AgentComponent.Unknown
        assertEquals("future_thing", unknown.type)
        assertTrue(unknown.raw.containsKey("extra"))
    }
}
