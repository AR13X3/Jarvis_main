package com.ar13x.jarvis.core.di

import com.ar13x.jarvis.core.data.AgentRepository
import com.ar13x.jarvis.core.data.DashboardRepository
import com.ar13x.jarvis.core.data.RoutineRepository
import com.ar13x.jarvis.core.data.SummaryRepository
import com.ar13x.jarvis.core.data.TaskRepository
import com.ar13x.jarvis.core.data.TodoRepository
import com.ar13x.jarvis.core.network.RemoteAgentRepository
import com.ar13x.jarvis.core.network.RemoteDashboardRepository
import com.ar13x.jarvis.core.network.RemoteRoutineRepository
import com.ar13x.jarvis.core.network.RemoteSummaryRepository
import com.ar13x.jarvis.core.network.RemoteTaskRepository
import com.ar13x.jarvis.core.network.RemoteTodoRepository
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
     * Real from the start, like the dashboard and unlike the routine: the whole
     * to-do domain is gateway state, every route is live, and there is no
     * ambiguity in the contract to resolve first. `FakeTodoRepository` stays for
     * tests and for working off the tailnet.
     */
    @Binds
    @Singleton
    abstract fun bindTodoRepository(impl: RemoteTodoRepository): TodoRepository

    /**
     * **Real, as of 2026-09-02.** The routine tab now reads Joy's actual routine
     * and start log instead of a fixture.
     *
     * Both reasons this was held back are resolved, and by measurement rather
     * than by deciding to risk it:
     *
     *  1. **The weekday base is 1-based.** `GET /routine` returns `1..7`, and
     *     `GET /routine/now` independently says `weekday=2` for a `logical_day`
     *     of 2026-09-01, which is a Tuesday. `RoutineDto.toDomain` derives this
     *     from the payload and lands on the same answer. Tracker 117 still asks
     *     for the range in the schema, so the derivation can become a constant.
     *  2. **The real response decodes.** `LiveContractTest` ran against a
     *     production body: seven days, every `category_key` resolving, every
     *     slot kind known.
     *
     * **What is still unverified: the WRITE path.** `routine/starts` currently
     * holds zero rows, so no real `SlotStart` has ever been decoded and no
     * `POST` has ever been made. The body mirrors the schema field for field,
     * and the first tap on a slot is the test. It is still the better binding:
     * a failed write surfaces as an error, whereas the fake accepted every tap
     * and lost them all on process death.
     */
    @Binds
    @Singleton
    abstract fun bindRoutineRepository(impl: RemoteRoutineRepository): RoutineRepository

    /**
     * Summaries (v2 plan §7). **Remote with no fake alongside it**, which is the
     * only repository here without one.
     *
     * A summary is facts the gateway counted plus prose a model wrote from them.
     * A fixture would have to invent both, and invented sentences about invented
     * arithmetic — on the one screen whose whole purpose is telling Joy what
     * actually happened — is a fiction this app should not be able to produce
     * even by accident. Off the tailnet the screen says so instead.
     */
    @Binds
    @Singleton
    abstract fun bindSummaryRepository(impl: RemoteSummaryRepository): SummaryRepository
}
