package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.data.DashboardRepository
import com.ar13x.jarvis.core.model.Dashboard
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real half of the dashboard seam. A translation layer and nothing more.
 *
 * Note there is no fallback and no partial result. If the call fails the screen
 * shows a failure; it does not show a dashboard with some sections missing.
 * Half a dashboard is indistinguishable from a quiet week, which is the one
 * thing §6 says this screen must never do.
 */
@Singleton
class RemoteDashboardRepository @Inject constructor(
    private val api: JarvisApi,
) : DashboardRepository {

    override suspend fun dashboard(from: LocalDate?, to: LocalDate?): Dashboard = gatewayCall {
        api.dashboard(
            // Bare calendar days on the wire. Formatting an Instant here would
            // reintroduce §3.2's bug: the server runs UTC, so a Sydney evening
            // lands on the following UTC date.
            dateFrom = from?.toString(),
            dateTo = to?.toString(),
        )
    }
}
