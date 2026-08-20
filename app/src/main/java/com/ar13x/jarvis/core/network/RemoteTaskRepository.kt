package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.data.TaskRepository
import com.ar13x.jarvis.core.model.CancelScope
import com.ar13x.jarvis.core.model.PagedTasks
import com.ar13x.jarvis.core.model.SectionsResponse
import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.model.TaskStatus
import com.ar13x.jarvis.core.model.UpcomingOccurrences
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real half of the §4 seam. Same interface the fake implements, so nothing
 * above it changes when the Hilt binding swaps.
 *
 * Note what is *not* here: no cache, no local database, no write queue. The
 * gateway owns all state (parent plan §2.1), so this class is a translation
 * layer and nothing more.
 */
@Singleton
class RemoteTaskRepository @Inject constructor(
    private val api: JarvisApi,
    private val json: Json,
) : TaskRepository {

    override suspend fun sections(): SectionsResponse =
        gatewayCall { api.sections() }

    override suspend fun tasks(
        status: TaskStatus?,
        from: LocalDate?,
        to: LocalDate?,
        page: Int,
    ): PagedTasks = gatewayCall {
        api.tasks(
            status = status?.wireName(),
            // Bare calendar days on the wire, which is what the server filters
            // on. Formatting an Instant here would reintroduce §3.2's bug.
            dateFrom = from?.toString(),
            dateTo = to?.toString(),
            page = page,
        )
    }

    override suspend fun setPriority(taskId: Long, isPriority: Boolean): Task =
        gatewayCall(mutating = true) {
            api.patchTask(taskId, PatchTaskBody(isPriority)).readTask(json)
        }

    override suspend fun cancel(taskId: Long, scope: CancelScope): Task =
        gatewayCall(mutating = true) {
            api.cancelTask(
                taskId,
                CancelTaskBody(confirm = true, scope = scope.wireName()),
            ).readTask(json)
        }

    /**
     * There is no `GET /tasks/{id}`, so a single task is read out of the paged
     * list. Flagged to gw03 in BUILD_NOTES §3.7 — a session opening on a task
     * currently costs a page of twenty to fetch one row.
     */
    override suspend fun task(taskId: Long): Task = gatewayCall {
        var page = 1
        while (true) {
            val result = api.tasks(page = page)
            result.tasks.firstOrNull { it.id == taskId }?.let { return@gatewayCall it }
            if (!result.hasMore) break
            page += 1
        }
        error("No task " + taskId)
    }

    override suspend fun upcomingOccurrences(withinHours: Int): UpcomingOccurrences =
        // The gateway caps this at 168 and 422s above it, so the app never asks
        // for more than a week regardless of what a caller passes.
        gatewayCall { api.upcomingOccurrences(withinHours.coerceIn(1, MAX_WINDOW_HOURS)) }

    private companion object {
        const val MAX_WINDOW_HOURS = 168
    }
}

/** The serialised name, so the enum's Kotlin casing never reaches the wire. */
internal fun TaskStatus.wireName(): String = when (this) {
    TaskStatus.Active -> "active"
    TaskStatus.Awaiting -> "awaiting"
    TaskStatus.Completed -> "completed"
    TaskStatus.Cancelled -> "cancelled"
    TaskStatus.Incomplete -> "incomplete"
}

internal fun CancelScope.wireName(): String = when (this) {
    CancelScope.Occurrence -> "occurrence"
    CancelScope.Series -> "series"
}
