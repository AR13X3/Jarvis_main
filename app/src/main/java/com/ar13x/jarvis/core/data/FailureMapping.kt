package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.FailureReason
import com.ar13x.jarvis.core.model.JarvisException
import java.io.IOException

/**
 * Turns a thrown thing into a [FailureReason] the UI can act on (plan §8.2).
 *
 * Kept on this side of the repository seam so that both implementations answer
 * in the same vocabulary, and so phase D can teach it about HTTP status codes
 * without any screen changing.
 *
 * The default is deliberately **not** [FailureReason.Unreachable]. Guessing
 * "check Tailscale" for an unrelated bug sends the user to toggle a VPN that was
 * never the problem, which is worse than admitting the error was unexpected.
 */
fun Throwable.toFailureReason(): FailureReason = when (this) {
    is JarvisException -> reason
    // DNS and connect failures to *.ts.net surface as IOException, and this is
    // the most likely failure by a wide margin — the phone off the tailnet.
    is IOException -> FailureReason.Unreachable
    else -> FailureReason.Unexpected(this::class.simpleName ?: "error")
}

/** The user-facing sentence for a reason (plan §8.2's table). */
fun FailureReason.message(): String = when (this) {
    FailureReason.Unreachable -> "Can't reach Jarvis. Is Tailscale connected?"
    FailureReason.Unauthorised -> "This device isn't authorised any more."
    FailureReason.Server -> "Jarvis is having trouble. Try again in a moment."
    is FailureReason.Timeout ->
        if (sent) {
            "That took too long. Your message was sent — check the session."
        } else {
            "That took too long. Nothing was sent."
        }
    is FailureReason.TooOld -> "This version of Jarvis is too old to talk to the server."
    is FailureReason.Api -> error.message.ifBlank { "Something went wrong." }
    is FailureReason.Unexpected -> "Something went wrong."
}

/** Only [FailureReason.Unreachable] has a one-tap fix worth offering. */
val FailureReason.offersTailscale: Boolean
    get() = this == FailureReason.Unreachable
