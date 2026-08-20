package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.BuildConfig
import com.ar13x.jarvis.core.data.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the shared bearer token (plan §4.1) and the build identity (§10.1).
 *
 * The token is held in an [AtomicReference] kept current by collecting the
 * store, rather than read per request: an interceptor is not a coroutine, and
 * blocking on DataStore inside OkHttp's chain would put a disk read on every
 * call and risk deadlocking the dispatcher under load.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    tokenStore: TokenStore,
    scope: CoroutineScope,
) : Interceptor {

    private val token = AtomicReference<String?>(null)

    init {
        tokenStore.token.onEach(token::set).launchIn(scope)
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
                token.get()?.let { header("Authorization", "Bearer " + it) }
            }
            .build()
        return chain.proceed(request)
    }
}
