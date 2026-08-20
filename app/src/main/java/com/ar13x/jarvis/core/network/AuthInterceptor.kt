package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.BuildConfig
import com.ar13x.jarvis.core.data.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the shared bearer token (plan §4.1) and the build identity (§10.1).
 *
 * Reads [TokenStore.current] rather than blocking on DataStore: an interceptor
 * is not a coroutine, and a disk read inside OkHttp's chain would land on every
 * request and risk deadlocking the dispatcher under load.
 *
 * The store keeps that value written *before* `save()` returns, which is what
 * makes pairing work — the first authenticated request happens immediately
 * after saving, and anything derived from a flow emission would not be there yet.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    scope: CoroutineScope,
) : Interceptor {

    init {
        // Primes `current` at cold start, when nothing has saved this process.
        tokenStore.token.launchIn(scope)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            // Costs nothing and means gateway logs attribute a bad turn to a
            // specific build (§10.1).
            .header(
                "X-App-Version",
                BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
            )
            .apply {
                tokenStore.current?.let { header("Authorization", "Bearer " + it) }
            }
            .build()
        return chain.proceed(request)
    }
}
