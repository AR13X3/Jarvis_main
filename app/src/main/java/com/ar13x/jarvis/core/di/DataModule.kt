package com.ar13x.jarvis.core.di

import com.ar13x.jarvis.core.data.AgentRepository
import com.ar13x.jarvis.core.data.TaskRepository
import com.ar13x.jarvis.core.network.RemoteAgentRepository
import com.ar13x.jarvis.core.network.RemoteTaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * **The fake ↔ real seam** (plan §0).
 *
 * Phase D swapped these two `@Binds` from the fakes to the Retrofit
 * implementations. Nothing above this file changed — no ViewModel, no screen,
 * no test — which is what the seam was for.
 *
 * The fakes are not deleted. They remain the backing for the unit tests, which
 * construct them directly, and for working on the app while off the tailnet:
 * point these bindings back at `FakeTaskRepository` / `FakeAgentRepository` and
 * the whole app runs on fixtures again.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: RemoteTaskRepository): TaskRepository

    @Binds
    @Singleton
    abstract fun bindAgentRepository(impl: RemoteAgentRepository): AgentRepository
}
