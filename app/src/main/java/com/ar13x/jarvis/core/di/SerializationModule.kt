package com.ar13x.jarvis.core.di

import com.ar13x.jarvis.core.network.JarvisJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SerializationModule {

    /** One instance, shared by the Retrofit converter and anything else. */
    @Provides
    @Singleton
    fun provideJson(): Json = JarvisJson
}
