package com.ar13x.jarvis.core.di

import com.ar13x.jarvis.core.update.GithubReleasesApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The GitHub client, kept separate from the gateway's on purpose.
 *
 * **This client must never carry `AuthInterceptor`.** That interceptor attaches
 * the gateway bearer token to every request it sees (plan §4.1), and reusing the
 * app's `OkHttpClient` here would send that token to github.com on every update
 * check — handing a third party a credential for the tailnet service, in a
 * request that by design succeeds when nothing else does.
 *
 * Short timeouts because nothing waits on this: the check is background,
 * once a day, and failure is a no-op (§10.2).
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    @GithubClient
    fun provideGithubOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @GithubClient
    fun provideGithubRetrofit(@GithubClient client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(GithubReleasesApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideGithubApi(@GithubClient retrofit: Retrofit): GithubReleasesApi =
        retrofit.create(GithubReleasesApi::class.java)
}

/** Distinguishes the token-free GitHub client from the gateway's. */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GithubClient
