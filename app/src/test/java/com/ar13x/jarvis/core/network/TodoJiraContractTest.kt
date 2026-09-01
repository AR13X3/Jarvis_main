package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.model.Todo
import com.ar13x.jarvis.core.model.TodoEvent
import com.ar13x.jarvis.core.model.TodoEvents
import com.ar13x.jarvis.core.model.TodoPriority
import com.ar13x.jarvis.core.model.TodoStatus
import com.ar13x.jarvis.core.model.TodoVerb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * §9.4 — priority, sub-tasks and the activity trail, checked against the
 * **contract itself** rather than against what this file remembers of it.
 *
 * `docs/gateway-openapi.json` is the copy gw03 deployed on 2026-09-02 (sha
 * `383d102ebc0df899`, 25 paths, 71 schemas), pulled from
 * `https://gw03.tail9662e3.ts.net/api/openapi.json`. Reading the spellings out
 * of it means these tests cannot agree with a mistake by repeating it.
 *
 * **Why this is worth a file of its own.** `JarvisJson` sets
 * `coerceInputValues`, so an enum the app spells differently from the gateway
 * **fails silently** — the value becomes the field's default rather than
 * throwing. That is not a hypothetical: `OverdueResolution` shared one value out
 * of four with the contract, answered overdue cards decoded as unanswered, and
 * live buttons stayed on screen until the whole extension allowance was spent.
 * Two new enums land here at once.
 */
class TodoJiraContractTest {

    // BUILD_NOTES §7: Python on this machine defaults to cp1252 and mangles the
    // §s in these descriptions. The JVM has no such default — but the encoding
    // is named anyway, because the file is full of them and a default is what
    // bit last time.
    private val contract: JsonObject = Json.parseToJsonElement(
        File("../docs/gateway-openapi.json")
            .takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?: File("docs/gateway-openapi.json").readText(Charsets.UTF_8),
    ).jsonObject

    private fun enumOf(schema: String): List<String> =
        contract["components"]!!.jsonObject["schemas"]!!.jsonObject[schema]!!
            .jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }

    // --- the two new enums --------------------------------------------------

    @Test
    fun `every priority is spelled the way the contract spells it`() {
        assertEquals(enumOf("TodoPriority"), TodoPriority.entries.map { it.wireName() })
    }

    @Test
    fun `declaration order is the sort order, most important first`() {
        // gw03 mirrors the Postgres enum's declaration order deliberately, so
        // "most important first" is `sortedBy { it.priority }` with no CASE
        // expression and no integer column kept in step with the labels. If this
        // reverses, every ordered list quietly inverts.
        assertEquals(
            listOf(
                TodoPriority.Highest,
                TodoPriority.High,
                TodoPriority.Normal,
                TodoPriority.Low,
            ),
            TodoPriority.entries.sortedBy { it.ordinal },
        )
    }

    @Test
    fun `every verb the contract publishes is one this app knows`() {
        val published = enumOf("TodoVerb")

        assertEquals(published, TodoVerb.entries.map { it.wire })
        for (wire in published) {
            assertNotNull("no TodoVerb for " + wire, TodoVerb.of(wire))
        }
    }

    // --- the degradation promised to gw03 on tracker 136 --------------------

    @Test
    fun `a verb this build does not know survives as its raw string`() {
        // The promise made when asking for the route. `verb` is a String rather
        // than an enum precisely so an eleventh verb degrades instead of either
        // throwing (a required enum with no default) or being silently coerced
        // into whichever value is declared first.
        val json = """
            {"event_id":9,"verb":"snoozed","at":"2026-09-02T04:00:00Z",
             "local_day":"2026-09-02"}
        """.trimIndent()

        val event = JarvisJson.decodeFromString(TodoEvent.serializer(), json)

        assertEquals("snoozed", event.verb)
        assertNull("an unknown verb must not masquerade as a known one", event.known)
    }

    @Test
    fun `a known verb resolves, and carries its typed detail`() {
        val json = """
            {"event_id":4,"verb":"renamed","at":"2026-09-01T22:10:00Z",
             "local_day":"2026-09-02","actor":"user",
             "detail":{"fields":["title"],
                       "changes":[{"field":"title","before":"Old","after":"New"}]}}
        """.trimIndent()

        val event = JarvisJson.decodeFromString(TodoEvent.serializer(), json)

        assertEquals(TodoVerb.Renamed, event.known)
        assertEquals(java.time.LocalDate.of(2026, 9, 2), event.localDay)
        assertEquals("title", event.detail?.changes?.single()?.field)
        assertEquals("Old", event.detail?.changes?.single()?.before)
    }

    @Test
    fun `an old event with fields but no changes decodes, and says nothing false`() {
        // The six events recorded before 2026-09-01 carry `fields` alone: the
        // old value was overwritten in place and no copy was kept. Absent
        // `changes` is UNRECOVERABLE, not "nothing changed", and the DTO has to
        // be able to represent the difference.
        val json = """
            {"event_id":1,"verb":"moved","at":"2026-08-20T01:00:00Z",
             "local_day":"2026-08-20","detail":{"fields":["due_at"]}}
        """.trimIndent()

        val event = JarvisJson.decodeFromString(TodoEvent.serializer(), json)

        assertEquals(listOf("due_at"), event.detail?.fields)
        assertNull(event.detail?.changes)
    }

    @Test
    fun `an event with no detail at all decodes`() {
        val json = """
            {"event_id":2,"verb":"done","at":"2026-09-01T05:00:00Z","local_day":"2026-09-01"}
        """.trimIndent()

        val event = JarvisJson.decodeFromString(TodoEvent.serializer(), json)

        assertNull(event.detail)
        // The documented default, not an accident of the payload.
        assertEquals("user", event.actor)
    }

    @Test
    fun `a page of history decodes`() {
        val json = """
            {"events":[{"event_id":2,"verb":"created","at":"2026-09-01T05:00:00Z",
                        "local_day":"2026-09-01","detail":{"title":"x","dated":false}}],
             "page":1,"has_more":false}
        """.trimIndent()

        val events = JarvisJson.decodeFromString(TodoEvents.serializer(), json)

        assertEquals(1, events.events.size)
        assertEquals(false, events.hasMore)
        assertEquals(false, events.events.single().detail?.dated)
    }

    // --- the to-do's own new fields -----------------------------------------

    @Test
    fun `a to-do decodes priority and its sub-task rollups`() {
        val json = """
            {"todo_id":7,"title":"Ship 9.4","status":"open","priority":"highest",
             "parent_id":null,"child_count":5,"child_done":3,
             "created_at":"2026-09-01T05:00:00Z","updated_at":"2026-09-02T05:00:00Z"}
        """.trimIndent()

        val todo = JarvisJson.decodeFromString(Todo.serializer(), json)

        assertEquals(TodoPriority.Highest, todo.priority)
        assertTrue(todo.hasChildren)
        assertTrue("a top-level to-do is not a sub-task", !todo.isSubTask)
        assertEquals(3, todo.childDone)
        assertEquals(5, todo.childCount)
    }

    @Test
    fun `a to-do with no priority key is normal, not null`() {
        // `priority` is NOT NULL with a default server-side, and the app must
        // agree: "not set" and "normal" are the same thing on a screen, and
        // making them different values sorts unpredictably.
        val json = """
            {"todo_id":8,"title":"Older row","status":"open",
             "created_at":"2026-09-01T05:00:00Z","updated_at":"2026-09-01T05:00:00Z"}
        """.trimIndent()

        val todo = JarvisJson.decodeFromString(Todo.serializer(), json)

        assertEquals(TodoPriority.Normal, todo.priority)
        assertEquals(0, todo.childCount)
    }

    @Test
    fun `a sub-task knows it is one`() {
        val json = """
            {"todo_id":9,"title":"A part of it","status":"doing","parent_id":7,
             "created_at":"2026-09-01T05:00:00Z","updated_at":"2026-09-01T05:00:00Z"}
        """.trimIndent()

        val todo = JarvisJson.decodeFromString(Todo.serializer(), json)

        assertTrue(todo.isSubTask)
        assertEquals(7L, todo.parentId)
        assertEquals(TodoStatus.Doing, todo.status)
    }

    // --- writing it back ----------------------------------------------------

    @Test
    fun `setting a priority mentions priority and nothing else`() {
        val body = todoPatchBody(priority = Patch.Set(TodoPriority.High))

        assertEquals(setOf("priority"), body.keys)
        assertEquals("high", body.getValue("priority").jsonPrimitive.content)
    }
}
