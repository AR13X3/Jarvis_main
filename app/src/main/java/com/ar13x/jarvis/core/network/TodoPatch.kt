package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.model.TodoPriority
import com.ar13x.jarvis.core.model.TodoStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/**
 * One field of a `PATCH`, in three states rather than two.
 *
 * **This exists because the app could not otherwise say "clear the deadline".**
 * `PATCH /todos/{id}` reads its body with `exclude_unset`, so an *omitted* key
 * means "leave this alone" and an explicit `null` means "clear it" — and gw03
 * singles that distinction out as mattering here more than anywhere else in the
 * API, because clearing a deadline is an ordinary edit (§5.2): it is how a dated
 * to-do goes back to the backlog.
 *
 * A Kotlin data class cannot express it. `JarvisJson` sets
 * `explicitNulls = false`, which *omits* a null field rather than writing it —
 * deliberately, and correctly, for every other body in this app. So
 * `PatchTodoBody(dueAt = null)` would serialise to `{}`: the user asks to clear
 * a deadline, the app sends "change nothing", the server agrees, and the screen
 * shows the deadline still there. No error anywhere.
 *
 * Flipping `explicitNulls` globally would be worse — then every unset field on
 * every patch would arrive as an explicit null, and one edit would blank the
 * rest of the record. The distinction is per-field, so the type is per-field.
 */
sealed interface Patch<out T> {

    /** Not mentioned. The key is omitted and the server leaves the field alone. */
    data object Unchanged : Patch<Nothing>

    /** Set to this value. */
    data class Set<T>(val value: T) : Patch<T>

    /**
     * Explicitly cleared — the key is sent as `null`.
     *
     * Only meaningful for a field the contract declares nullable. Asking to
     * clear `title` would be a 422, which is the right answer.
     */
    data object Clear : Patch<Nothing>
}

/**
 * `PATCH /todos/{id}`, built as a [JsonObject] so that omission and null stay
 * distinguishable all the way to the wire.
 *
 * Every argument defaults to [Patch.Unchanged], so a caller changing one field
 * mentions one field — and cannot silently blank the others by forgetting them,
 * which is the failure a nullable data class invites.
 */
fun todoPatchBody(
    title: Patch<String> = Patch.Unchanged,
    description: Patch<String> = Patch.Unchanged,
    startsAt: Patch<Instant?> = Patch.Unchanged,
    dueAt: Patch<Instant?> = Patch.Unchanged,
    status: Patch<TodoStatus> = Patch.Unchanged,
    tags: Patch<List<String>> = Patch.Unchanged,
    priority: Patch<TodoPriority> = Patch.Unchanged,
): JsonObject = buildMap {
    put("title", title) { JsonPrimitive(it) }
    put("description", description) { JsonPrimitive(it) }
    put("starts_at", startsAt) { instant -> instant?.let { JsonPrimitive(it.toString()) } ?: JsonNull }
    put("due_at", dueAt) { instant -> instant?.let { JsonPrimitive(it.toString()) } ?: JsonNull }
    put("status", status) { JsonPrimitive(it.wireName()) }
    put("tags", tags) { list -> JsonArray(list.map(::JsonPrimitive)) }
    // `Clear` would be a 422: the contract allows null here but the column is
    // NOT NULL with a default, so "no priority" is not a state. Setting `normal`
    // is how you say ordinary, and that is a value rather than an absence.
    put("priority", priority) { JsonPrimitive(it.wireName()) }
}.let(::JsonObject)

/**
 * Adds the key only when the field was actually mentioned.
 *
 * The whole point is the `Unchanged` branch doing nothing: an absent key is a
 * different request from a null one, and the difference has to survive down to
 * the map.
 */
private inline fun <T> MutableMap<String, kotlinx.serialization.json.JsonElement>.put(
    key: String,
    patch: Patch<T>,
    encode: (T) -> kotlinx.serialization.json.JsonElement,
) {
    when (patch) {
        Patch.Unchanged -> Unit
        Patch.Clear -> put(key, JsonNull)
        is Patch.Set -> put(key, encode(patch.value))
    }
}

/**
 * The wire spelling. The enum's own names are Kotlin-cased.
 *
 * Written out rather than `name.lowercase()` on purpose: an exhaustive `when`
 * fails to compile when a value is added, where `lowercase()` would silently
 * produce a string the gateway may or may not recognise — and an enum the app
 * spells differently fails SILENTLY here, because `coerceInputValues` turns an
 * unrecognised value into the default rather than throwing.
 */
fun TodoPriority.wireName(): String = when (this) {
    TodoPriority.Highest -> "highest"
    TodoPriority.High -> "high"
    TodoPriority.Normal -> "normal"
    TodoPriority.Low -> "low"
}

/** The wire spelling. The enum's own names are Kotlin-cased. */
fun TodoStatus.wireName(): String = when (this) {
    TodoStatus.Open -> "open"
    TodoStatus.Doing -> "doing"
    TodoStatus.Done -> "done"
    TodoStatus.Cancelled -> "cancelled"
}
