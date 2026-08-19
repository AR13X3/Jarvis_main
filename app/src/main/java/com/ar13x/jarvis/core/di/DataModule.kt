package com.ar13x.jarvis.core.di

import com.ar13x.jarvis.core.data.AgentRepository
import com.ar13x.jarvis.core.data.FakeAgentRepository
import com.ar13x.jarvis.core.data.FakeTaskRepository
import com.ar13x.jarvis.core.data.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * **The fake ↔ real seam** (plan §0).
 *
 * This is the whole reason Hilt is in the stack: phases A–C run entirely on
 * fixtures with no network code executing, and phase D swaps the two `@Binds`
 * below to the Retrofit implementations. Nothing above this file — no ViewModel,
 * no screen, no test — changes when that happens.
 *
 * The fakes are not deleted at that point. They stay as the backing for UI tests
 * and for working on the app while off the tailnet.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: FakeTaskRepository): TaskRepository

    @Binds
    @Singleton
    abstract fun bindAgentRepository(impl: FakeAgentRepository): AgentRepository
}
