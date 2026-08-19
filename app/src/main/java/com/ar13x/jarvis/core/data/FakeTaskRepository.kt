package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.CancelScope
import com.ar13x.jarvis.core.model.PagedTasks
import com.ar13x.jarvis.core.model.SectionsResponse
import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.model.TaskStatus
import com.ar13x.jarvis.core.model.UpcomingOccurrence
import com.ar13x.jarvis.core.model.UpcomingOccurrences
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fixture-backed [TaskRepository] for phases A–C, and permanently the backing
 * for UI tests and for working on the app while off-tailnet (plan §0).
 *
 * The section queries live here rather than in a ViewModel on purpose: they are
 * the *server's* queries (parent plan §2.7), so keeping them on this side of the
 * seam means the ViewModel is written against the same shape either
 * implementation returns, and swapping in Retrofit changes no UI code.
 */
@Singleton
class FakeTaskRepository @Inject constructor(
    private val backend: FakeBackend,
) : TaskRepository {

    override suspend fun sections(): SectionsResponse {
        delay(FakeBackend.READ_LATENCY_MS)
        val all = backend.allTasks()

        // These are views over one table, not task types (parent plan §2.7).
        // A task that is both priority and recurring therefore appears in both —
        // built duplicated on purpose so §5.2's open question can be judged on
        // the device rather than argued about beforehand.
        val priority = all
            .filter { it.isPriority && (it.status == TaskStatus.Active || it.status == TaskStatus.Awaiting) }
            .sortedBy { it.dueAt }

        val recurring = all
            .filter { it.isRecurring && it.status == TaskStatus.Active }
            .sortedBy { it.nextFireAt ?: it.dueAt }

        return SectionsResponse(
            priority = priority,
            recurring = recurring,
            all = page(all.sortedByDescending { it.createdAt }, page = 1),
        )
    }

    override suspend fun tasks(
        statuses: Set<TaskStatus>,
        from: LocalDate?,
        to: LocalDate?,
        page: Int,
    ): PagedTasks {
        delay(FakeBackend.READ_LATENCY_MS)
        val filtered = backend.allTasks()
            .asSequence()
            .filter { statuses.isEmpty() || it.status in statuses }
            // Filtered on the LOCAL calendar day the server sent, never on the
            // instant — the whole point of `due_date` existing (plan §3.2).
            .filter { from == null || !it.dueDate.isBefore(from) }
            .filter { to == null || !it.dueDate.isAfter(to) }
            .sortedByDescending { it.createdAt }
            .toList()
        return page(filtered, page)
    }

    override suspend fun setPriority(taskId: Long, isPriority: Boolean): Task {
        delay(FakeBackend.WRITE_LATENCY_MS)
        return backend.setPriority(taskId, isPriority)
    }

    override suspend fun cancel(taskId: Long, scope: CancelScope): Task {
        delay(FakeBackend.WRITE_LATENCY_MS)
        return backend.cancel(taskId, scope)
    }

    override suspend fun task(taskId: Long): Task {
        delay(FakeBackend.READ_LATENCY_MS)
        return requireNotNull(backend.task(taskId)) { "No task " + taskId }
    }

    override suspend fun upcomingOccurrences(withinHours: Int): UpcomingOccurrences {
        delay(FakeBackend.READ_LATENCY_MS)
        val now = Instant.now()
        val until = now.plus(withinHours.toLong(), ChronoUnit.HOURS)
        val occurrences = backend.allTasks()
            .filter { !it.status.isTerminal }
            .mapNotNull { task ->
                val fireAt = task.nextFireAt ?: task.dueAt
                if (fireAt.isBefore(now) || fireAt.isAfter(until)) return@mapNotNull null
                UpcomingOccurrence(
                    occurrenceId = task.id * 1_000 + 1,
                    taskId = task.id,
                    title = task.title,
                    scheduledFor = fireAt,
                    isPriority = task.isPriority,
                )
            }
            .sortedBy { it.scheduledFor }

        return UpcomingOccurrences(
            occurrences = occurrences,
            windowHours = withinHours,
            generatedAt = now,
        )
    }

    private fun page(source: List<Task>, page: Int): PagedTasks {
        val size = FakeBackend.PAGE_SIZE
        val start = (page - 1).coerceAtLeast(0) * size
        val slice = source.drop(start).take(size)
        return PagedTasks(
            tasks = slice,
            page = page,
            hasMore = start + slice.size < source.size,
        )
    }
}
