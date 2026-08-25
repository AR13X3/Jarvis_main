package com.ar13x.jarvis.core.update

import com.ar13x.jarvis.BuildConfig
import com.ar13x.jarvis.core.network.JarvisApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this build is current, behind, or too old to talk to the gateway
 * (plan §10.2, §10.3).
 *
 * Client and server update out of band — the gateway is redeployed on `gw03`
 * whenever, and the phone updates whenever Obtainium is opened — so they *will*
 * drift. This is that drift handled explicitly rather than debugged later as a
 * mystery.
 *
 * Two sources on two different cadences, deliberately:
 *
 * - **`/health`, every foreground.** It is one cheap unauthenticated call to a
 *   machine on the tailnet, and it decides whether to block, which is too
 *   consequential to answer from something stale.
 * - **GitHub, at most once a day.** Rate limits are not the reason (§10.2 does
 *   the arithmetic: 0.07% of the anonymous allowance); it is simply that a
 *   release does not appear more than once a day and polling faster buys
 *   nothing.
 *
 * **Everything here fails silently.** A failed update check is not worth an
 * error — the network being down is the normal state of a Tailscale-only app
 * whose owner is on mobile data, and an app that complains about it is an app
 * that cries wolf.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val github: GithubReleasesApi,
    private val gateway: JarvisApi,
    private val store: UpdateStore,
) {
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.UpToDate)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    /** The installed build. Null only if `versionName` is malformed, which the build script prevents. */
    private val installed: SemVer? = SemVer.parseOrNull(BuildConfig.VERSION_NAME)

    /**
     * From a live `/health`, never persisted — see [UpdateStore]. Held across
     * calls so a single failed refresh does not silently lift a block that is
     * genuinely in force.
     */
    @Volatile
    private var minSupported: SemVer? = null

    private val refreshing = Mutex()

    /**
     * Called on app foreground. Safe to call often — the daily guard is inside.
     *
     * [force] skips the guard, for the About screen's explicit check. Only ever
     * reached by someone deliberately asking, so it cannot turn into a poll.
     */
    suspend fun refresh(force: Boolean = false) {
        // Two foregrounds in quick succession (a permission dialog returning,
        // say) must not both fire the GitHub call and race on the timestamp.
        if (!refreshing.tryLock()) return
        try {
            runCatching { gateway.health() }.getOrNull()?.let { health ->
                minSupported = SemVer.parseOrNull(health.minSupportedApp)
            }

            val remembered = store.remembered.first()
            val now = System.currentTimeMillis()
            if (force || now - remembered.lastCheckedAt >= CHECK_INTERVAL_MS) {
                val release = runCatching { github.latestRelease() }.getOrNull()
                    // A draft or pre-release is not something to offer: the
                    // channel only ships finished releases, and Obtainium would
                    // not install it anyway.
                    ?.takeIf { !it.draft && !it.prerelease }
                store.recordCheck(now, release)
            }

            _status.value = computeStatus(store.remembered.first())
        } finally {
            refreshing.unlock()
        }
    }

    /**
     * Every release at or above the installed build, newest first.
     *
     * Fetched on demand rather than on the daily cadence: About is opened
     * deliberately and rarely, and someone reading it wants what is true now.
     * It is also the one call in the app that works off-tailnet, so it answers
     * even when the gateway does not.
     *
     * The installed version is included and marked [ReleaseNote.current], so the
     * screen can answer "what am I running" as well as "what would I get". If
     * this build is not on the list at all — a local build, or one pulled from
     * the channel — the list is simply what is newer, which is still the honest
     * answer.
     *
     * Failure returns empty rather than throwing. A changelog that could not be
     * fetched is a blank section, not an error dialog on a diagnostics screen.
     */
    suspend fun whatsNew(): List<ReleaseNote> {
        val installed = installed ?: return emptyList()

        return runCatching { github.releases() }.getOrNull().orEmpty()
            .toReleaseNotes(installed)
    }

    suspend fun dismiss(version: SemVer) {
        store.dismiss(version)
        _status.value = computeStatus(store.remembered.first())
    }

    private fun computeStatus(remembered: UpdateStore.Remembered): UpdateStatus {
        val installed = installed ?: return UpdateStatus.UpToDate

        val latest = SemVer.parseOrNull(remembered.latestTag)
        val available = if (latest != null && latest > installed) {
            AvailableUpdate(
                version = latest,
                notes = remembered.latestNotes?.takeIf { it.isNotBlank() },
                releaseUrl = remembered.latestUrl ?: GithubReleasesApi.RELEASES_PAGE,
            )
        } else {
            null
        }

        // Blocking outranks the banner. Below the minimum the app cannot work,
        // so offering a dismissible suggestion would be a lie.
        minSupported?.let { minimum ->
            if (installed < minimum) {
                return UpdateStatus.Blocked(
                    installed = installed,
                    minSupported = minimum,
                    update = available,
                )
            }
        }

        if (available != null && remembered.dismissedVersion != available.version.toString()) {
            return UpdateStatus.Available(available)
        }
        return UpdateStatus.UpToDate
    }

    private companion object {
        val CHECK_INTERVAL_MS = TimeUnit.DAYS.toMillis(1)
    }
}

/**
 * The releases a build should show, newest first.
 *
 * Separated from the fetch so it can be tested without a network or an Android
 * context — the filtering is where the behaviour is, and the call around it is
 * one line.
 *
 * Drafts and pre-releases are excluded for the same reason the update check
 * excludes them: the channel only ships finished releases, and Obtainium would
 * not install one anyway. Anything older than the installed build is excluded
 * because it is not news; the installed build itself is kept and marked, so the
 * screen can answer "what am I running" as well as "what would I get".
 */
internal fun List<GithubRelease>.toReleaseNotes(installed: SemVer): List<ReleaseNote> = this
    .asSequence()
    .filterNot { it.draft || it.prerelease }
    .mapNotNull { release ->
        val version = SemVer.parseOrNull(release.tagName) ?: return@mapNotNull null
        if (version < installed) return@mapNotNull null
        ReleaseNote(
            version = version,
            notes = release.body.orEmpty().trim(),
            publishedAt = release.publishedAt,
            current = version == installed,
        )
    }
    .sortedByDescending { it.version }
    .toList()
