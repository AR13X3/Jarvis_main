package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.model.ApiError
import com.ar13x.jarvis.core.model.FailureReason
import com.ar13x.jarvis.core.model.JarvisException
import com.ar13x.jarvis.core.model.Task
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Turns transport and HTTP failures into the taxonomy §8.2 is written against.
 *
 * The order matters: [SocketTimeoutException] is an [IOException], so a timeout
 * checked after the generic IO branch would always be reported as "check
 * Tailscale" — sending the user to toggle a VPN that was working fine.
 */
suspend fun <T> gatewayCall(
    /** True only for a request that is safe to describe as "already sent". */
    mutating: Boolean = false,
    block: suspend () -> T,
): T = try {
    block()
} catch (e: SocketTimeoutException) {
    // Whether the message reached the server decides which sentence the user
    // sees, and getting it wrong either loses a turn or duplicates one (§8.2).
    throw JarvisException(FailureReason.Timeout(sent = mutating), "Timed out", e)
} catch (e: HttpException) {
    throw JarvisException(e.toFailureReason(), e.message(), e)
} catch (e: IOException) {
    // DNS and connect failures to *.ts.net land here. By a wide margin this is
    // the phone being off the tailnet.
    throw JarvisException(FailureReason.Unreachable, "Cannot reach the gateway", e)
}

private fun HttpException.toFailureReason(): FailureReason = when (code()) {
    401, 403 -> FailureReason.Unauthorised
    in 500..599 -> FailureReason.Server
    else -> FailureReason.Api(parseError())
}

/**
 * The gateway speaks two error dialects: the plan's `{"error":{code,message}}`
 * envelope, and FastAPI's `{"detail": …}` for validation failures. Both are read
 * here so a 422 says something specific instead of "something went wrong".
 */
private fun HttpException.parseError(): ApiError {
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull().orEmpty()
    if (body.isBlank()) return ApiError(code().toString(), "Request failed (" + code() + ")")

    val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        ?: return ApiError(code().toString(), "Request failed (" + code() + ")")

    json["error"]?.let { error ->
        val obj = runCatching { error.jsonObject }.getOrNull()
        if (obj != null) {
            return ApiError(
                code = obj["code"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                message = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }

    json["detail"]?.let { detail ->
        val text = runCatching { detail.jsonPrimitive.contentOrNull }.getOrNull()
            ?: detail.toString()
        return ApiError(code = code().toString(), message = text)
    }

    return ApiError(code().toString(), "Request failed (" + code() + ")")
}

/**
 * Reads a task out of a mutation response.
 *
 * `PATCH /tasks/{id}`, `POST /tasks/{id}/cancel` and
 * `POST /proposals/{id}/confirm` are declared in the OpenAPI document as bare
 * objects with no schema — FastAPI did not annotate their return types — while
 * plan §4.4 says they return `{task}`. Rather than guess, this accepts either
 * an envelope or a bare task, so an annotation landing later cannot break the
 * app. Flagged to gw03 in BUILD_NOTES §3.6.
 */
fun JsonObject.readTask(json: Json): Task {
    val envelope = this["task"]
    val target = if (envelope is JsonObject) envelope else this
    return json.decodeFromJsonElement(Task.serializer(), target)
}
