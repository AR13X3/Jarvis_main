package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.model.Todo
import com.ar13x.jarvis.core.model.TodoStatus
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * `PATCH /todos/{id}` — omission and null are **different requests**, and the
 * app has to be able to send both.
 *
 * The server reads this body with `exclude_unset`: an absent key means "leave
 * this alone", an explicit `null` means "clear it". gw03 calls that distinction
 * more load-bearing here than anywhere else in the API, and they are right —
 * clearing a deadline is how a dated to-do goes back to the backlog (§5.2), so
 * it is an ordinary edit rather than an edge case.
 *
 * The app could not express it. `JarvisJson` sets `explicitNulls = false`, which
 * *omits* null fields — correct for every other body here, and fatal for this
 * one: a nullable `dueAt = null` would serialise to `{}`, the server would
 * change nothing, and the screen would show the deadline still sitting there
 * with no error anywhere. These tests are what stop that being reintroduced by
 * someone simplifying `todoPatchBody` into a data class.
 */
class TodoPatchTest {

    private val deadline = Instant.parse("2026-09-04T09:00:00Z")

    @Test
    fun `an unmentioned field is omitted entirely`() {
        val body = todoPatchBody(status = Patch.Set(TodoStatus.Doing))

        assertEquals(setOf("status"), body.keys)
        assertEquals("doing", body.getValue("status").jsonPrimitive.content)
    }

    @Test
    fun `clearing a deadline sends an explicit null, not an omission`() {
        // The whole reason this file exists. `{}` here would mean "change
        // nothing" and the deadline would survive.
        val body = todoPatchBody(dueAt = Patch.Clear)

        assertTrue("due_at must be present", "due_at" in body)
        assertEquals(JsonNull, body.getValue("due_at"))
    }

    @Test
    fun `setting a deadline sends the instant`() {
        val body = todoPatchBody(dueAt = Patch.Set(deadline))

        assertEquals("2026-09-04T09:00:00Z", body.getValue("due_at").jsonPrimitive.content)
    }

    @Test
    fun `Set of a null value is a clear, not an omission`() {
        // `Patch<Instant?>` allows `Set(null)`, and a reader could reasonably
        // expect that to mean the same as `Clear`. It does. What it must not do
        // is fall through to omission.
        val body = todoPatchBody(dueAt = Patch.Set(null))

        assertTrue("due_at" in body)
        assertEquals(JsonNull, body.getValue("due_at"))
    }

    @Test
    fun `changing one field mentions exactly one field`() {
        // The failure a nullable data class invites: every unmentioned field
        // arriving as null and blanking the record. Renaming a to-do must not
        // touch its deadline, its tags or its status.
        val body = todoPatchBody(title = Patch.Set("A better title"))

        assertEquals(setOf("title"), body.keys)
        assertFalse("due_at" in body)
        assertFalse("tags" in body)
        assertFalse("status" in body)
    }

    @Test
    fun `an empty patch is empty, not a request to blank everything`() {
        assertTrue(todoPatchBody().isEmpty())
    }

    @Test
    fun `tags are sent as an array, and an empty list clears them`() {
        // Distinct from Clear: `[]` means "no tags", `null` means "leave the
        // tags alone". Both are legal and they are different.
        assertEquals(0, todoPatchBody(tags = Patch.Set(emptyList())).getValue("tags").let {
            (it as kotlinx.serialization.json.JsonArray).size
        })
        assertEquals(JsonNull, todoPatchBody(tags = Patch.Clear).getValue("tags"))
    }

    @Test
    fun `several fields at once each keep their own state`() {
        val body = todoPatchBody(
            title = Patch.Set("Renamed"),
            dueAt = Patch.Clear,
            status = Patch.Set(TodoStatus.Open),
            // description and tags left alone
        )

        assertEquals(setOf("title", "due_at", "status"), body.keys)
        assertEquals(JsonNull, body.getValue("due_at"))
        assertEquals("Renamed", body.getValue("title").jsonPrimitive.content)
        assertEquals("open", body.getValue("status").jsonPrimitive.content)
    }

    @Test
    fun `the serialised body keeps the null, rather than dropping it on the way out`() {
        // The end-to-end version, through the actual Json instance Retrofit
        // uses. `explicitNulls = false` applies to data classes; a JsonObject
        // carries what it was built with, and this asserts that is still true.
        val json = JarvisJson.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            todoPatchBody(dueAt = Patch.Clear),
        )

        assertEquals("""{"due_at":null}""", json)
    }

    @Test
    fun `every status has a wire spelling the contract recognises`() {
        // The enum is Kotlin-cased and the wire is lower-case. A mismatch would
        // be a 422 rather than silence, but only for the value that was wrong.
        assertEquals(
            listOf("open", "doing", "done", "cancelled"),
            TodoStatus.entries.map { it.wireName() },
        )
    }

    @Test
    fun `a todo decodes from the contract's own field names`() {
        val json = """
            {"todo_id":2,"title":"Off-machine backups","description":"d",
             "starts_at":"2026-08-30T09:00:00Z","due_at":"2026-09-04T09:00:00Z",
             "starts_on":"2026-08-30","due_date":"2026-09-04","status":"doing",
             "tags":["CBAI"],"task_ids":[31],
             "created_at":"2026-08-28T09:00:00Z","updated_at":"2026-09-01T07:00:00Z"}
        """.trimIndent()

        val todo = JarvisJson.decodeFromString(Todo.serializer(), json)

        assertEquals(2L, todo.todoId)
        assertEquals(TodoStatus.Doing, todo.status)
        assertEquals(listOf("CBAI"), todo.tags)
        assertEquals(deadline, todo.dueAt)
        assertEquals(java.time.LocalDate.of(2026, 9, 4), todo.dueDate)
        assertFalse(todo.isUndated)
        // The payload above still carries `task_ids`, and getting this far is
        // the assertion. The gateway serves it until gw03 retires
        // `todos.todo_tasks` (tracker 131/132); the app stopped decoding it in
        // §9.5. What is pinned here is that the decode SURVIVES the key — a
        // field arriving from a gateway ahead of the app must never take the
        // screen down, which is what `ignoreUnknownKeys` is for.
    }

    @Test
    fun `an undated todo decodes, which is the whole point of the backlog`() {
        // The shape that would have crashed the task list if `tasks.due_at` had
        // been made nullable as the plan's stale §5.2 paragraph described. Here
        // it is ordinary, and the DTO says so.
        val json = """
            {"todo_id":1,"title":"Place the uni weeks","status":"open",
             "created_at":"2026-08-14T02:11:00Z","updated_at":"2026-08-14T02:11:00Z"}
        """.trimIndent()

        val todo = JarvisJson.decodeFromString(Todo.serializer(), json)

        assertNull(todo.dueAt)
        assertNull(todo.dueDate)
        assertTrue(todo.isUndated)
        // `task_ids` is absent here too, and is no longer a field either way.
        assertEquals("", todo.description)
    }
}
