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
     * **Still the fake, and now that is a choice rather than a lack.**
     *
     * The routes exist — `GET /routine`, `/routine/now`, `/routine/starts` —
     * and [com.ar13x.jarvis.core.network.RemoteRoutineRepository] is written and
     * tested against payloads built from the served schema. Flipping this line
     * is the whole swap.
     *
     * It is not flipped yet, and the reason is not caution in general but two
     * specific things:
     *
     *  1. **The weekday encoding is not in the contract.** `RoutineDay.weekday`
     *     is a bare integer, Python has both 0-based and 1-based conventions,
     *     and getting it wrong shifts the entire week by a day while leaving
     *     every slot and time correct — so it reads as bad data, not as a client
     *     bug. `RoutineDto.toDomain` decides it from the payload rather than
     *     guessing, but that has never met a real response. Tracker 117.
     *  2. **Nothing here has been run against the live gateway.** There is no
     *     bearer token on the build machine, so every routine payload this code
     *     has seen was written from the schema. On this project the bugs that
     *     matter have consistently been found by using the app on the phone.
     *
     * Flipping it unverified risks a blank routine tab — a working feature
     * traded for an unrun one. So: one live read first, then this line.
     */
    @Binds
    @Singleton
    abstract fun bindRoutineRepository(impl: FakeRoutineRepository): RoutineRepository
}
