package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.Dashboard
import java.time.LocalDate

/**
 * `GET /dashboard` — v2 plan §6.
 *
 * The same §4 seam as [TaskRepository]: one interface, a fake over fixtures and
 * a Retrofit implementation, chosen by a Hilt binding.
 *
 * There is no cache and no local aggregation, and that is §6 rather than an
 * omission — *the app renders; the gateway computes every number*. A cached
 * dashboard would also be the one thing on this screen that could disagree with
 * the provenance it is shown beside, which is exactly the failure the screen
 * exists to prevent.
 */
interface DashboardRepository {

    /**
     * Nulls mean **the gateway's default window**, not "all time" and not a
     * window this app picked. Deciding what "this period" means on the client
     * would be the same mistake as a client-side drift threshold: a number the
     * server owns, guessed here, disagreeing silently the day it is tuned.
     */
    suspend fun dashboard(from: LocalDate? = null, to: LocalDate? = null): Dashboard
}
