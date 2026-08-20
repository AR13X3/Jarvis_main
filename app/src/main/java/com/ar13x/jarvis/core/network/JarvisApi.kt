package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.model.AgentResponse
import com.ar13x.jarvis.core.model.PagedMessages
import com.ar13x.jarvis.core.model.PagedTasks
import com.ar13x.jarvis.core.model.SectionsResponse
import com.ar13x.jarvis.core.model.Session
import com.ar13x.jarvis.core.model.UpcomingOccurrences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
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
     * Note `date_from` / `date_to`, not the `from` / `to` the plan's §4.4 shows,
     * and `status` is a **single** value — see BUILD_NOTES §3.5.
     */
    @GET("tasks")
    suspend fun tasks(
        @Query("status") status: String? = null,
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

    @POST("sessions")
    suspend fun createSession(@Body body: CreateSessionBody): Session

    /** Not yet served — see BUILD_NOTES §3.12. The schema is already theirs. */
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

    @POST("proposals/{id}/reject")
    suspend fun rejectProposal(@Path("id") proposalId: String): JsonObject
}

// --- request bodies -----------------------------------------------------------

@Serializable
data class PatchTaskBody(@SerialName("is_priority") val isPriority: Boolean)

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

// --- responses ----------------------------------------------------------------

/**
 * `/health` is the only endpoint the app can reach without a token, which makes
 * it the right probe for the first-run screen: it separates "cannot reach the
 * gateway" from "the token is wrong" before the user has entered anything.
 */
@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val backend: String = "",
    @SerialName("min_supported_app") val minSupportedApp: String? = null,
    @SerialName("current_app") val currentApp: String? = null,
)
