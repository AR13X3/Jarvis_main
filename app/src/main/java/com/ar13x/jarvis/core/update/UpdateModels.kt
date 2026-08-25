package com.ar13x.jarvis.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The slice of GitHub's release payload the app actually reads (plan §10.2). */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    /** Release notes. Becomes the text the banner shows, so it is written for the phone. */
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

/**
 * One release, as About reads it.
 *
 * [current] marks the build actually installed — worth distinguishing, because
 * "what am I running" and "what would I get" are different questions and the
 * screen answers both.
 */
data class ReleaseNote(
    val version: SemVer,
    val notes: String,
    val publishedAt: String?,
    val current: Boolean = false,
)

/** A newer build, as far as the app knows. */
data class AvailableUpdate(
    val version: SemVer,
    val notes: String?,
    val releaseUrl: String?,
)

/**
 * What the app should say about its own version, if anything.
 *
 * Two different situations with two different answers (plan §10.3), which is
 * why this is one type rather than a pair of booleans that can both be true:
 * being too old to talk to the gateway *blocks*, being merely behind *informs*.
 */
sealed interface UpdateStatus {

    /** Current, or nothing known yet. The overwhelmingly common case. */
    data object UpToDate : UpdateStatus

    /** Newer release exists and has not been dismissed — a dismissible banner. */
    data class Available(val update: AvailableUpdate) : UpdateStatus

    /**
     * Below the gateway's `min_supported_app` — a **blocking** screen.
     *
     * Blocking is correct here per §10.3: a client sending a retired request
     * shape produces confusing failures rather than clean ones, so a wall that
     * explains itself beats a series of errors that do not.
     */
    data class Blocked(
        val installed: SemVer,
        val minSupported: SemVer,
        val update: AvailableUpdate?,
    ) : UpdateStatus
}
