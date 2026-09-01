package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * A to-do's activity trail (§9.4.3, tracker 136 — live since 2026-09-02).
 *
 * **Why it is worth a route.** The dashboard can say a to-do has gone stale; only
 * the trail can say whether it was touched-and-deferred or genuinely forgotten,
 * and those two are identical in `updated_at` — which is the one field a reader
 * would otherwise reach for.
 *
 * ### The verb is a `String` here, and that is the whole point
 *
 * `JarvisJson` sets `coerceInputValues`, which turns an unrecognised enum value
 * into the field's default — and a required enum with **no** default throws
 * instead. Either way a gateway that adds an eleventh verb breaks this screen:
 * silently mislabelling every such row, or taking the whole list down.
 *
 * That is not hypothetical. `OverdueResolution` shared one value out of four with
 * the contract, answered cards decoded as unanswered, and live buttons stayed on
 * screen until the extension allowance was spent — the app spelling an enum
 * differently from the gateway is the most expensive class of bug this project
 * has had, precisely because nothing fails.
 *
 * So the wire value is kept verbatim and [known] does the mapping. An unknown
 * verb renders as its own raw string: less pretty, and never wrong.
 */
@Serializable
data class TodoEvent(
    @SerialName("event_id") val eventId: Long,

    /** The raw wire verb. See the class note — deliberately not an enum. */
    val verb: String,

    @Serializable(InstantSerializer::class)
    val at: Instant,

    /**
     * The **server's** local day for this event (§3.2).
     *
     * Present on every row, so grouping the trail by day never derives a
     * calendar day from [at] on the phone.
     */
    @Serializable(LocalDateSerializer::class)
    @SerialName("local_day") val localDay: LocalDate,

    /** The status the to-do was left in, where the verb implies one. */
    val status: TodoStatus? = null,

    /**
     * Who did it. Defaults to `user`, and today it is always `user`.
     *
     * gw03 added the column truthfully rather than inventing an agent/user split
     * that the data does not support (tracker 139). The UI draws it only when
     * more than one value actually appears — a column that never varies is not
     * information.
     */
    val actor: String = "user",

    val detail: TodoEventDetail? = null,
) {
    /** The verb, if this app knows it. `null` means the gateway is ahead. */
    val known: TodoVerb? get() = TodoVerb.of(verb)
}

/**
 * What changed, in the domain's own terms.
 *
 * **One component with optional fields, not ten one per verb.** gw03 was offered
 * either and chose this, correctly: a discriminated union of ten shapes is more
 * precise and costs ten rendering branches for a list where most rows carry a
 * single key.
 *
 * Which verb populates which field:
 * ```
 *   created             title, dated
 *   tagged / untagged   tag
 *   linked / unlinked   task_id
 *   done / cancelled /
 *     reopened / moved  was — the status it came FROM
 *   renamed / moved     fields, and changes on rows recorded after 0013
 * ```
 *
 * **Absent means absent, and must not be read as "nothing changed".** The events
 * recorded before 2026-09-01 carry [fields] alone, because the old value was
 * overwritten in place and no copy was kept. That is *unrecoverable* rather than
 * *unrecorded* — the same shape as `extend_occurrence` once overwriting
 * `scheduled_for` and making every drift number look near zero.
 */
@Serializable
data class TodoEventDetail(
    val title: String? = null,
    /** For `created`: whether it arrived with a deadline or straight to the backlog. */
    val dated: Boolean? = null,
    val tag: String? = null,
    @SerialName("task_id") val taskId: Long? = null,
    /** The status it came *from*. */
    val was: TodoStatus? = null,
    /** Which fields changed, when the before/after was not kept. */
    val fields: List<String>? = null,
    /** Before and after, on rows recorded after migration 0013. */
    val changes: List<TodoFieldChange>? = null,
)

/**
 * One field, before and after.
 *
 * `before`/`after` rather than `from`/`to` because `from` is a Python keyword on
 * gw03's side; the same reason `SummaryFacts` carries `period_start`/`period_end`.
 *
 * Both are the **rendered string form**, dates included. The trail is read, not
 * computed against — anything wanting arithmetic on a to-do's old deadline should
 * be reading the to-do, not its history.
 *
 * [field] is a raw string for the same reason [TodoEvent.verb] is: it is a
 * required enum on the wire, and a fifth field name arriving would otherwise
 * throw rather than degrade.
 */
@Serializable
data class TodoFieldChange(
    val field: String,
    val before: String? = null,
    val after: String? = null,
)

/** Paged, newest first, because a trail only grows. */
@Serializable
data class TodoEvents(
    val events: List<TodoEvent> = emptyList(),
    val page: Int = 1,
    @SerialName("has_more") val hasMore: Boolean = false,
)

/**
 * The ten things that can happen to a to-do — `todos.todo_event`.
 *
 * Published by gw03 as a named enum in tracker 110. **Nothing decodes directly
 * into this**; it is reached through [TodoEvent.known], so an unrecognised verb
 * degrades to its raw string instead of being coerced into whichever value
 * happens to be declared first.
 *
 * `cancelled` and `done` are both endings and are not the same ending;
 * `reopened` is why neither is terminal.
 */
enum class TodoVerb(val wire: String) {
    Created("created"),
    Moved("moved"),
    Renamed("renamed"),
    Tagged("tagged"),
    Untagged("untagged"),
    Linked("linked"),
    Unlinked("unlinked"),
    Done("done"),
    Cancelled("cancelled"),
    Reopened("reopened"),
    ;

    companion object {
        private val byWire = entries.associateBy { it.wire }

        /** `null` when the gateway knows a verb this build does not. */
        fun of(wire: String): TodoVerb? = byWire[wire]
    }
}
