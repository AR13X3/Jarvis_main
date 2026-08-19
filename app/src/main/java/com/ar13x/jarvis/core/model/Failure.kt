package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Non-2xx bodies are `{"error": {"code", "message"}}` (plan §4.2). */
@Serializable
data class ApiErrorEnvelope(val error: ApiError)

@Serializable
data class ApiError(
    val code: String = "",
    /** Safe to show the user verbatim. */
    val message: String = "",
)

/**
 * The failure taxonomy behind §8.2's messages.
 *
 * This is an enum of *causes*, not of strings, because the UI has to do more
 * than print them: [Unreachable] offers a button that opens Tailscale,
 * [Unauthorised] sends you back to the token screen, and [TooOld] blocks the
 * app entirely. A generic "Something went wrong" is close to useless when the
 * most likely failure by a wide margin is the phone being off the tailnet and
 * the fix is one toggle.
 */
sealed interface FailureReason {
    /** DNS/connect failure to `*.ts.net` — almost always Tailscale being off. */
    data object Unreachable : FailureReason

    /** 401. The token screen, not a generic error. */
    data object Unauthorised : FailureReason

    /** 5xx. */
    data object Server : FailureReason

    /**
     * Timed out mid-turn. [sent] distinguishes "your message reached the server,
     * check the session" from "nothing happened, retry" — saying the wrong one
     * either loses a turn or duplicates it.
     */
    data class Timeout(val sent: Boolean) : FailureReason

    /** Below the gateway's `min_supported_app` (plan §10.3). Blocking. */
    data class TooOld(val minSupported: String) : FailureReason

    /** A structured error the gateway gave us; [ApiError.message] is displayable. */
    data class Api(val error: ApiError) : FailureReason

    data class Unexpected(val cause: String) : FailureReason
}

class JarvisException(
    val reason: FailureReason,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)
