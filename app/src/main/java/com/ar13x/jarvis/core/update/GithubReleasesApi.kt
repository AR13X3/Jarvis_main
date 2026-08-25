package com.ar13x.jarvis.core.update

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The releases repo (plan §10, §10.2) — public, and holding no code, so nothing
 * here is authenticated and nothing here is private.
 *
 * **Named `Jarvis_2.0`, not the plan's `jarvis-releases`.** The split the plan
 * asks for is intact — private source in `AR13X3/Jarvis_main`, public releases
 * here — only the names differ. The property that matters is that this one is
 * public: Obtainium then needs no GitHub token, and its token setting is global
 * across every source, so configuring one would put a standing credential for
 * every private repo on the account onto the phone.
 *
 * Two properties make this the one network call worth trusting (§10.2):
 *
 * - **It works off-tailnet.** api.github.com is public, unlike the gateway, so
 *   the update check succeeds in exactly the situation where every other call
 *   fails — which is often when someone wants to know if they are out of date.
 * - **Rate limits are irrelevant.** Unauthenticated GitHub allows 60 requests an
 *   hour per IP; once a day is 0.07% of that. No token, so nothing to leak.
 *
 * This runs on its own OkHttp client with **no `AuthInterceptor`** — see
 * `UpdateModule`. Sending the gateway's bearer token to github.com would hand a
 * third party a credential it has no business holding.
 */
interface GithubReleasesApi {

    /**
     * Every release, newest first.
     *
     * The About screen needs the *list*, not just the newest: someone on 0.1.3
     * when 0.1.7 lands has missed three releases, and `latest` describes only
     * the last of them. Reading "what changed" should mean everything since the
     * build you are holding.
     */
    @GET("repos/{owner}/{repo}/releases")
    suspend fun releases(
        @Path("owner") owner: String = RELEASES_OWNER,
        @Path("repo") repo: String = RELEASES_REPO,
        @Query("per_page") perPage: Int = 20,
        @Header("X-GitHub-Api-Version") apiVersion: String = "2022-11-28",
        @Header("Accept") accept: String = "application/vnd.github+json",
    ): List<GithubRelease>

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(
        @Path("owner") owner: String = RELEASES_OWNER,
        @Path("repo") repo: String = RELEASES_REPO,
        // Pinning the API version keeps a future default change from altering
        // the shape underneath a build that is already in the field.
        @Header("X-GitHub-Api-Version") apiVersion: String = "2022-11-28",
        @Header("Accept") accept: String = "application/vnd.github+json",
    ): GithubRelease

    companion object {
        const val RELEASES_OWNER = "AR13X3"
        const val RELEASES_REPO = "Jarvis_2.0"
        const val BASE_URL = "https://api.github.com/"

        /** Where to send someone when Obtainium is not installed. */
        const val RELEASES_PAGE = "https://github.com/$RELEASES_OWNER/$RELEASES_REPO/releases/latest"
    }
}
