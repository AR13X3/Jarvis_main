package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.CancelScope
import com.ar13x.jarvis.core.model.PagedTasks
import com.ar13x.jarvis.core.model.SectionsResponse
import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.model.TaskStatus
import com.ar13x.jarvis.core.model.UpcomingOccurrences
import java.time.LocalDate

/**
 * **§4 is the seam.** This interface has two implementations from day one —
 * [FakeTaskRepository] over fixtures, and (from phase D) a Retrofit-backed one.
 * A Hilt module swap picks which, so phases A–C are fully interactive with zero
 * network code running.
 *
 * The fakes are not throwaway: they stay as the backing for UI tests and for
 * working on the app while off-tailnet.
 *
 * Note there is no local task database and no offline write queue. The gateway
 * owns all state (parent plan §2.1), and reconciling an offline write against an
 * agent that may have moved the task on is a genuinely hard problem with no
 * reason to exist here (plan §3.5). Failures surface; they do not queue.
 */
interface TaskRepository {

    /** One round trip for the whole Tasks tab first paint. */
    suspend fun sections(): SectionsResponse

    suspend fun tasks(
        statuses: Set<TaskStatus> = emptySet(),
        from: LocalDate? = null,
        to: LocalDate? = null,
        page: Int = 1,
    ): PagedTasks

    /**
     * The one deliberate exception to "never write without a proposal" (§3.4).
     * Direct manipulation of a cheap, reversible flag does not confirm — an undo
     * snackbar is what makes that defensible (§5.2).
     */
    suspend fun setPriority(taskId: Long, isPriority: Boolean): Task

    /**
     * Cancel is confirmed from either path; it is not cheaply reversible.
     *
     * [scope] only means something for a recurring task — skipping this firing
     * and calling off the rule are different intentions (see [CancelScope]).
     * For a one-shot task both scopes do the same thing.
     */
    suspend fun cancel(taskId: Long, scope: CancelScope = CancelScope.Series): Task

    suspend fun task(taskId: Long): Task

    /** The ~48h window the device mirrors to set exact alarms (§7.1). */
    suspend fun upcomingOccurrences(withinHours: Int = 48): UpcomingOccurrences
}
