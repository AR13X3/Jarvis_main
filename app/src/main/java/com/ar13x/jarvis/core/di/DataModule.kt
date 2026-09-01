package com.ar13x.jarvis.core.di

import com.ar13x.jarvis.core.data.AgentRepository
import com.ar13x.jarvis.core.data.DashboardRepository
import com.ar13x.jarvis.core.data.FakeRoutineRepository
import com.ar13x.jarvis.core.data.RoutineRepository
import com.ar13x.jarvis.core.data.TaskRepository
import com.ar13x.jarvis.core.network.RemoteAgentRepository
import com.ar13x.jarvis.core.network.RemoteDashboardRepository
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

    /**
     * Real from the day the screen was written — unlike the routine, the whole
     * dashboard already exists on the gateway (`GET /dashboard`, contract
     * `e398ff18e4aa6b33`). `FakeDashboardRepository` stays for tests and for
     * working off the tailnet; swap this line to reach for it.
     */
    @Binds
    @Singleton
    abstract fun bindDashboardRepository(impl: RemoteDashboardRepository): DashboardRepository

    /**
     * Still a fake, and visibly so.
     *
     * The routine template, its versions and the start log are all gateway state
     * in the v2 plan, and none of those routes exist yet. This binding is the
     * one place that changes when they do — the same seam the two above went
     * through in phase D.
     */
    @Binds
    @Singleton
    abstract fun bindRoutineRepository(impl: FakeRoutineRepository): RoutineRepository
}
