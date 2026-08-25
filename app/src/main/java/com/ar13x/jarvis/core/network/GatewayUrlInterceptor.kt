package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.BuildConfig
import com.ar13x.jarvis.core.data.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends requests to the gateway the user actually paired with.
 *
 * **Fixes a real defect** (BUILD_NOTES §8.7): the pairing screen has always had
 * a URL field, `TokenStore` has always saved what was typed into it, and
 * nothing has ever read it back. Retrofit's base URL was fixed at build time
 * from `BuildConfig.DEFAULT_GATEWAY_URL`, so a different URL was accepted,
 * stored, and then silently ignored — the app carried on talking to the
 * compiled-in host. Harmless while there is one gateway; it becomes real the
 * day it moves, and presents as "I changed the URL and nothing happened".
 *
 * Retrofit needs *a* base URL at construction, so the compiled-in one stays as
 * the placeholder and this rewrites scheme, host, port and path prefix on the
 * way out. Same shape as `AuthInterceptor`: an interceptor is not a coroutine,
 * so it reads a `@Volatile` off [TokenStore] rather than blocking on DataStore
 * inside OkHttp's chain.
 *
 * **The prefix matters and is the fiddly part.** `tailscale serve` mounts the
 * gateway at `/api` and strips it, so the stored URL carries that segment while
 * the Retrofit paths do not. Swapping only the host would drop it and every
 * call would 404 — so the stored path is prepended to the request's own.
 */
@Singleton
class GatewayUrlInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    scope: CoroutineScope,
) : Interceptor {

    init {
        // Primes `currentUrl` at cold start, when nothing has saved this process.
        tokenStore.gatewayUrl.launchIn(scope)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val target = tokenStore.currentUrl
            // Not paired yet, or paired before this field meant anything. The
            // compiled-in base URL is already correct in both cases.
            ?.takeIf { it.isNotBlank() }
            ?.toHttpUrlOrNull()
            ?: return chain.proceed(request)

        val rewritten = request.url.rebased(from = RETROFIT_BASE, to = target)
        if (rewritten == request.url) return chain.proceed(request)

        return chain.proceed(request.newBuilder().url(rewritten).build())
    }

    private companion object {
        /**
         * What Retrofit was constructed with, and therefore the prefix already
         * baked into every outgoing path. Needed because the rewrite is a
         * *replacement*, not an addition — see [rebased].
         */
        val RETROFIT_BASE: HttpUrl = BuildConfig.DEFAULT_GATEWAY_URL.toHttpUrl()
    }
}

/**
 * Moves this URL from base [from] onto base [to], keeping the path below the
 * prefix, and the query, untouched.
 *
 * **Both bases are needed, and that is the whole subtlety.** Retrofit is built
 * with `.../api/`, so an outgoing path is *already* `/api/tasks/sections`.
 * Prepending the target's prefix without removing the old one produces
 * `/api/api/tasks/sections` — which is what the first version of this did, and
 * what the tests caught. The rewrite is a replacement, not an addition.
 *
 * Written as a free function so it can be tested without an OkHttp chain: all
 * the behaviour worth pinning is here, and the interceptor is plumbing.
 */
internal fun HttpUrl.rebased(from: HttpUrl, to: HttpUrl): HttpUrl {
    val oldPrefix = from.encodedPath.trimEnd('/')
    val newPrefix = to.encodedPath.trimEnd('/')
    val below = encodedPath.removePrefix(oldPrefix).ifEmpty { "/" }

    return newBuilder()
        .scheme(to.scheme)
        .host(to.host)
        .port(to.port)
        .encodedPath(newPrefix + below)
        .build()
}
