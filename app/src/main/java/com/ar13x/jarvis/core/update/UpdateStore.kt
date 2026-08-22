package com.ar13x.jarvis.core.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the update check remembers between launches.
 *
 * Its own DataStore rather than a corner of `credentials`: none of this is
 * secret, and mixing it in with the Keystore-encrypted token would mean reading
 * and rewriting an encrypted file to record a timestamp.
 *
 * Deliberately **not** stored: the gateway's `min_supported_app`. That value
 * only decides whether to put a wall in front of the app, and a wall raised from
 * a cached number while the gateway is unreachable would tell someone to update
 * when the real problem is that Tailscale is off. It is held in memory, from a
 * live read, and nowhere else.
 */
private val Context.updates: DataStore<Preferences> by preferencesDataStore("updates")

@Singleton
class UpdateStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        val LAST_CHECKED = longPreferencesKey("last_checked_at")
        val LATEST_TAG = stringPreferencesKey("latest_tag")
        val LATEST_NOTES = stringPreferencesKey("latest_notes")
        val LATEST_URL = stringPreferencesKey("latest_url")
        val DISMISSED = stringPreferencesKey("dismissed_version")
    }

    data class Remembered(
        val lastCheckedAt: Long,
        val latestTag: String?,
        val latestNotes: String?,
        val latestUrl: String?,
        val dismissedVersion: String?,
    )

    val remembered: Flow<Remembered> = context.updates.data.map { prefs ->
        Remembered(
            lastCheckedAt = prefs[LAST_CHECKED] ?: 0L,
            latestTag = prefs[LATEST_TAG],
            latestNotes = prefs[LATEST_NOTES],
            latestUrl = prefs[LATEST_URL],
            dismissedVersion = prefs[DISMISSED],
        )
    }

    suspend fun recordCheck(at: Long, release: GithubRelease?) {
        context.updates.edit { prefs ->
            // The timestamp is written even when the fetch found nothing, so a
            // repo that does not exist yet is retried tomorrow rather than on
            // every single foreground.
            prefs[LAST_CHECKED] = at
            if (release != null) {
                prefs[LATEST_TAG] = release.tagName
                prefs[LATEST_NOTES] = release.body.orEmpty()
                prefs[LATEST_URL] = release.htmlUrl ?: GithubReleasesApi.RELEASES_PAGE
            }
        }
    }

    /**
     * Dismissal is **per version**, not a global "don't tell me".
     *
     * Storing a boolean would silence the banner forever after one dismissal,
     * so the next release — the one that might matter — would arrive silently.
     */
    suspend fun dismiss(version: SemVer) {
        context.updates.edit { it[DISMISSED] = version.toString() }
    }
}
