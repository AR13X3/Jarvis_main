package com.ar13x.jarvis.feature.update

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.ar13x.jarvis.core.update.GithubReleasesApi

/**
 * Obtainium if it is installed, the release page if it is not.
 *
 * **The app never downloads or installs the APK itself** (plan §10.2). Doing so
 * would need `REQUEST_INSTALL_PACKAGES`, a download manager, signature
 * verification and an install-session flow — all to duplicate a job Obtainium
 * already does, and does off-tailnet. This function only *notices* and *points*.
 *
 * Both Obtainium package ids are tried because the F-Droid build uses its own,
 * and someone who installed from there has Obtainium every bit as much.
 *
 * Requires the `<queries>` block in the manifest: from Android 11 a package the
 * app has not declared is invisible to `getLaunchIntentForPackage`, which
 * returns null rather than failing loudly — so the fallback would silently
 * become the only path.
 */
fun Context.openUpdateChannel(releaseUrl: String? = null) {
    val obtainium = OBTAINIUM_PACKAGES.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
    val intent = obtainium
        ?: Intent(Intent.ACTION_VIEW, (releaseUrl ?: GithubReleasesApi.RELEASES_PAGE).toUri())
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private val OBTAINIUM_PACKAGES = listOf(
    "dev.imranr.obtainium",
    "dev.imranr.obtainium.fdroid",
)
