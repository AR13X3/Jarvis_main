package com.ar13x.jarvis.core.di

import com.ar13x.jarvis.BuildConfig
import com.ar13x.jarvis.core.network.AuthInterceptor
import com.ar13x.jarvis.core.network.JarvisApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Application-lifetime scope, for work that outlives any one screen. */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideOkHttp(auth: AuthInterceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(auth)
            // Tailscale issues a real certificate for the *.ts.net name, so
            // standard trust works. If anyone ever adds a trust-manager override
            // here, something else is wrong — see plan §4.
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    // BASIC, not BODY: the bearer token rides in a header and
                    // task titles are personal. Neither belongs in logcat.
                    redactHeader("Authorization")
                },
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // Trailing slash is required: without it Retrofit resolves relative
        // paths against the parent and every call loses the /api mount.
        .baseUrl(BuildConfig.DEFAULT_GATEWAY_URL.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): JarvisApi = retrofit.create(JarvisApi::class.java)

    /**
     * The agent turn is the long one — the plan puts the median around 4s and a
     * slow model call can run far past that. A default 10s read timeout would
     * cut off perfectly good turns, which is the worst possible failure here
     * because §8.2 then has to say "your message was sent" and leave the user
     * to check.
     */
    private const val AGENT_TIMEOUT_SECONDS = 90L
}
