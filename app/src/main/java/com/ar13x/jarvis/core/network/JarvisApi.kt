package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.model.AgentResponse
import com.ar13x.jarvis.core.model.Dashboard
import com.ar13x.jarvis.core.model.PagedMessages
import com.ar13x.jarvis.core.model.PagedTasks
import com.ar13x.jarvis.core.model.PagedTodos
import com.ar13x.jarvis.core.model.SectionsResponse
import com.ar13x.jarvis.core.model.Session
import com.ar13x.jarvis.core.model.Todo
import com.ar13x.jarvis.core.model.TodoEnvelope
import com.ar13x.jarvis.core.model.UpcomingOccurrences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The gateway, exactly as `docs/gateway-openapi.json` describes it.
 *
 * Paths are relative: `tailscale serve` mounts the gateway at `/api` and strips
 * the prefix, so the base URL carries `/api` and the spec's paths do not.
 */
interface JarvisApi {

    /** Unauthenticated. Used by the connectivity probe and the compat gate. */
    @GET("health")
    suspend fun health(): HealthResponse

    @GET("tasks/sections")
    suspend fun sections(): SectionsResponse

    /**
     * Note `date_from` / `date_to`, not the `from` / `to` the plan's §4.4 shows.
     *
     * `status` is a **repeated** parameter. It was single-valued once and
     * BUILD_NOTES §3.5 asked for the array; the array is live (verified against
     * the served contract, sha `e398ff18e4aa6b33`).
     */
    @GET("tasks")
    suspend fun tasks(
        @Query("status") status: List<String>? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("page") page: Int = 1,
    ): PagedTasks

    /** Returns a bare [Task], not an envelope — deliberately, per gw03. */
    @GET("tasks/{id}")
    suspend fun task(@Path("id") id: Long): com.ar13x.jarvis.core.model.Task

    @PATCH("tasks/{id}")
    suspend fun patchTask(@Path("id") id: Long, @Body body: PatchTaskBody): JsonObject

    @POST("tasks/{id}/cancel")
    suspend fun cancelTask(@Path("id") id: Long, @Body body: CancelTaskBody): JsonObject

    @GET("occurrences/upcoming")
    suspend fun upcomingOccurrences(@Query("within_hours") withinHours: Int): UpcomingOccurrences

    /**
     * v2 plan §6. The gateway computes every number; this returns finished
     * arithmetic and the app does no bucketing, streaks or percentages of its own.
     *
     * Both dates are optional, and omitting them takes the **gateway's** default
     * window. An app-side default would be the client deciding what "this
     * period" means, which is the same class of mistake as an app-side drift
     * threshold.
     */
    @GET("dashboard")
    suspend fun dashboard(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
    ): Dashboard

    // --- to-dos (v2 plan §5) ---------------------------------------------------

    /**
     * The list. **The backlog is `undated_only=true`, not a second route** —
     * §5.2 gives undated to-dos their own place in the UI, and that is a filter
     * over one ordering rather than a different collection.
     *
     * Note `status` is a **list** here, unlike `GET /tasks` where it is a single
     * value (BUILD_NOTES §3.5). Retrofit repeats the key per element, which is
     * what FastAPI reads back into a list.
     */
    @GET("todos")
    suspend fun todos(
        @Query("status") status: List<String>? = null,
        @Query("tag") tag: String? = null,
        @Query("undated_only") undatedOnly: Boolean = false,
        @Query("page") page: Int = 1,
    ): PagedTodos

    @GET("todos/{id}")
    suspend fun todo(@Path("id") id: Long): Todo

    @POST("todos")
    suspend fun createTodo(@Body body: CreateTodoBody): TodoEnvelope

    /**
     * Takes a raw [JsonObject] rather than a data class, and that is load-bearing
     * — see [todoPatchBody]. An omitted key means "leave alone" and an explicit
     * `null` means "clear"; `JarvisJson` omits Kotlin nulls, so a data class
     * could only ever send the first of those.
     */
    @PATCH("todos/{id}")
    suspend fun patchTodo(@Path("id") id: Long, @Body body: JsonObject): TodoEnvelope

    // `POST`/`DELETE todos/{todoId}/tasks/{taskId}` are still served and are
    // deliberately not declared here. They attached a reminder to a to-do; Joy
    // had that removed because a to-do's own deadline is the deadline and two
    // mechanisms answering one question have to be kept agreeing forever. gw03
    // is retiring the table (tracker 131/132) — a route the app can still call
    // is a route the app can still be tempted back into.

    // --- routine (v2 plan §4) --------------------------------------------------
    //
    // `GET routine` is a fetch-once-and-keep object, not a per-view read: §4.2
    // requires the routine tab to render off the tailnet.

    @GET("routine")
    suspend fun routine(
        @Query("on") on: String? = null,
        @Query("version_id") versionId: Long? = null,
    ): RoutineDto

    /**
     * A cross-check, deliberately **not** load-bearing (§4.2, tracker 84). The
     * app resolves the logical day locally from the boundaries this same server
     * declared; this exists so the two answers can be compared, and a
     * disagreement is a bug that is only findable because both exist.
     */
    @GET("routine/now")
    suspend fun routineNow(): RoutineNowDto

    /** Both dates required: a default window here would have to pick between
     *  "this routine week" and "the last seven days", and those differ by which
     *  week Sunday belongs to. */
    @GET("routine/starts")
    suspend fun routineStarts(
        @Query("date_from") dateFrom: String,
        @Query("date_to") dateTo: String,
        @Query("version_id") versionId: Long? = null,
    ): SlotStartsDto

    @POST("routine/starts")
    suspend fun recordStart(@Body body: RecordStartBody): SlotStartDto

    /**
     * Answers `ok` whether or not there was a start to clear — clearing an
     * already-clear slot is the state the caller asked for.
     */
    @DELETE("routine/starts")
    suspend fun clearStart(
        @Query("version_id") versionId: Long,
        @Query("slot_key") slotKey: String,
        @Query("on") on: String,
    ): JsonObject

    @POST("sessions")
    suspend fun createSession(@Body body: CreateSessionBody): Session

    /**
     * The chat-history list (BUILD_NOTES §3.12).
     *
     * **Served now.** It 404'd when the app half was written, which is why every
     * caller wraps it in `runCatching` — that tolerance is what let the feature
     * ship ahead of the route, and it is why nothing had to change when the
     * route appeared. Probed 2026-09-02: `401`, where a nonsense path `404`s.
     */
    @GET("sessions")
    suspend fun sessions(
        @Query("kind") kind: String,
        @Query("page") page: Int = 1,
    ): com.ar13x.jarvis.core.model.PagedSessions

    @GET("sessions/{id}/messages")
    suspend fun messages(
        @Path("id") sessionId: String,
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int = 30,
    ): PagedMessages

    @POST("sessions/{id}/messages")
    suspend fun sendMessage(@Path("id") sessionId: String, @Body body: SendMessageBody): AgentResponse

    /**
     * Pages a disambiguation card. Takes the cursor straight from the component,
     * so it costs no model call and invents no user turn.
     */
    @GET("sessions/{id}/options")
    suspend fun options(
        @Path("id") sessionId: String,
        @Query("cursor") cursor: String,
    ): com.ar13x.jarvis.core.model.AgentComponent.TaskOptions

    @POST("proposals/{id}/confirm")
    suspend fun confirmProposal(@Path("id") proposalId: String): JsonObject

    /**
     * Answers to an overdue nudge — see `docs/joy-to-gw03-07`.
     *
     * Occurrence-scoped: completing Monday's gym session must not close the
     * weekly rule (parent plan §2.5). A `409` from `extend` means the allowance
     * is spent; the app does not pre-empt that, because the count is the
     * server's and only the server knows it is current.
     */
    @POST("occurrences/{id}/complete")
    suspend fun completeOccurrence(@Path("id") occurrenceId: Long): JsonObject

    @POST("occurrences/{id}/extend")
    suspend fun extendOccurrence(
        @Path("id") occurrenceId: Long,
        @Body body: ExtendOccurrenceBody,
    ): JsonObject

    @POST("proposals/{id}/reject")
    suspend fun rejectProposal(@Path("id") proposalId: String): JsonObject
}

// --- request bodies -----------------------------------------------------------

@Serializable
/**
 * Both optional, so one call can carry either.
 *
 * `explicitNulls = false` on the Json instance means an unset field is omitted
 * rather than sent as null — which matters, because a `PATCH` that transmitted
 * `"description": null` would read as "clear it" to any reasonable server.
 *
 * `description` is a contract addition — see the ask in the tracker. Until gw03
 * accepts it, ticking a checkbox fails cleanly rather than silently doing
 * nothing.
 */
data class PatchTaskBody(
    @SerialName("is_priority") val isPriority: Boolean? = null,
    val description: String? = null,
)

@Serializable
data class CancelTaskBody(
    val confirm: Boolean = true,
    /** `occurrence` skips this firing; `series` ends the rule. */
    val scope: String,
)

@Serializable
data class CreateSessionBody(
    val kind: String,
    @SerialName("task_id") val taskId: Long? = null,
)

@Serializable
data class SendMessageBody(val text: String)

/**
 * Direct creation, **no proposal**.
 *
 * A task is proposed-and-confirmed because the model parses "next Thursday" and
 * can misread it. A to-do typed into a form cannot be misparsed — the same
 * reasoning that lets a priority toggle write directly (plan §5.4).
 *
 * Every field but `title` is optional here, and `explicitNulls = false` means an
 * unset one is omitted rather than sent as null. That is correct for a POST:
 * there is nothing yet to clear. The distinction only bites on PATCH, which is
 * why that body is built by hand — see `todoPatchBody`.
 */
@Serializable
data class CreateTodoBody(
    val title: String,
    val description: String? = null,
    @Serializable(com.ar13x.jarvis.core.model.InstantSerializer::class)
    @SerialName("starts_at") val startsAt: java.time.Instant? = null,
    @Serializable(com.ar13x.jarvis.core.model.InstantSerializer::class)
    @SerialName("due_at") val dueAt: java.time.Instant? = null,
    val tags: List<String>? = null,
)

// --- responses ----------------------------------------------------------------

/**
 * `/health` is the only endpoint the app can reach without a token, which makes
 * it the right probe for the first-run screen: it separates "cannot reach the
 * gateway" from "the token is wrong" before the user has entered anything.
 */
@Serializable
data class ExtendOccurrenceBody(val minutes: Int)

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val backend: String = "",
    @SerialName("min_supported_app") val minSupportedApp: String? = null,
    @SerialName("current_app") val currentApp: String? = null,
)
