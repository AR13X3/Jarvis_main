package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.CancelScope
import com.ar13x.jarvis.core.model.AgentResponse
import com.ar13x.jarvis.core.model.Message
import com.ar13x.jarvis.core.model.MessageRole
import com.ar13x.jarvis.core.model.ProposalAction
import com.ar13x.jarvis.core.model.ProposalStatus
import com.ar13x.jarvis.core.model.ProposalSummary
import com.ar13x.jarvis.core.model.Session
import com.ar13x.jarvis.core.model.SessionSummary
import com.ar13x.jarvis.core.model.SessionKind
import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.model.TaskStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The in-memory stand-in for the gateway (plan §0).
 *
 * One store shared by [FakeTaskRepository] and [FakeAgentRepository], so
 * confirming a proposal in a chat actually makes a task appear in the list —
 * without that, phases B and C cannot be judged honestly.
 *
 * State lives for the process only. That is the right level of fidelity: the
 * real gateway owns persistence, and building a local database here would
 * quietly become the source of truth the parent plan forbids (§2.1).
 */
@Singleton
class FakeBackend @Inject constructor() {

    companion object {
        /**
         * Deliberately slow. The plan puts the real median at ~4s and warns that
         * the pending state is therefore seen constantly and deserves real design
         * attention (§5.3). A fake that answered instantly would let that state
         * ship as a spinner nobody ever looked at.
         */
        const val READ_LATENCY_MS = 240L
        const val WRITE_LATENCY_MS = 420L
        const val AGENT_LATENCY_MS = 2_200L
        const val PAGE_SIZE = 20
        const val OPTIONS_PER_PAGE = 3
    }

    private val mutex = Mutex()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val tasks = linkedMapOf<Long, Task>()
    private val sessions = linkedMapOf<String, Session>()
    private val sessionByTask = hashMapOf<Long, String>()
    private val history = hashMapOf<String, MutableList<Message>>()
    private val proposals = hashMapOf<String, FakeProposal>()

    private var nextTaskId = 100L
    private var nextMessageId = 1L

    init {
        Fixtures.seed().forEach { tasks[it.id] = it }
    }

    // --- Tasks ----------------------------------------------------------------

    suspend fun allTasks(): List<Task> = mutex.withLock { tasks.values.toList() }

    suspend fun task(id: Long): Task? = mutex.withLock { tasks[id] }

    suspend fun setPriority(id: Long, isPriority: Boolean): Task = mutex.withLock {
        val existing = requireNotNull(tasks[id]) { "No task " + id }
        val updated = existing.copy(isPriority = isPriority, updatedAt = Instant.now())
        tasks[id] = updated
        updated
    }

    suspend fun cancel(id: Long, scope: CancelScope): Task = mutex.withLock {
        val existing = requireNotNull(tasks[id]) { "No task " + id }
        val now = Instant.now()

        // Skipping one firing of a recurring task leaves the rule alone: the
        // parent stays active and simply moves on to its next occurrence. Only
        // the series scope resolves the task itself.
        val skipOnly = scope == CancelScope.Occurrence && existing.recurrence != null
        val updated = if (skipOnly) {
            // The real scheduler computes the next occurrence from the RRULE.
            // The app must never do this (plan §3.1) — the fake may, because it
            // is standing in for the server.
            val advanced = (existing.nextFireAt ?: existing.dueAt).plus(7, ChronoUnit.DAYS)
            existing.copy(nextFireAt = advanced, updatedAt = now, dueToday = false)
        } else {
            // Rows are never deleted — cancelling sets status and cancelled_at.
            existing.copy(
                status = TaskStatus.Cancelled,
                cancelledAt = now,
                updatedAt = now,
                dueToday = false,
            )
        }
        tasks[id] = updated
        updated
    }

    // --- Sessions -------------------------------------------------------------

    suspend fun createSession(kind: SessionKind, taskId: Long?): Session = mutex.withLock {
        // One session per task, by construction — the same invariant the real
        // schema enforces with `unique (task_id)` (parent plan §2.2).
        if (taskId != null) {
            val existing = sessionByTask[taskId]
            if (existing != null) return@withLock sessions.getValue(existing)
        }
        val now = Instant.now()
        val session = Session(
            id = UUID.randomUUID().toString(),
            kind = kind,
            taskId = taskId,
            createdAt = now,
            updatedAt = now,
        )
        sessions[session.id] = session
        history[session.id] = mutableListOf()
        if (taskId != null) sessionByTask[taskId] = session.id
        session
    }

    suspend fun session(id: String): Session? = mutex.withLock { sessions[id] }

    /** Newest first, with a title taken from the first thing the user said. */
    suspend fun generalSessions(): List<SessionSummary> = mutex.withLock {
        sessions.values
            .filter { it.kind == SessionKind.General }
            .map { session ->
                val turns = history[session.id].orEmpty()
                SessionSummary(
                    id = session.id,
                    kind = session.kind,
                    title = turns.firstOrNull { it.role == MessageRole.User }
                        ?.text?.take(60),
                    updatedAt = turns.lastOrNull()?.createdAt ?: session.updatedAt,
                    messageCount = turns.size,
                )
            }
            .sortedByDescending { it.updatedAt }
    }

    /**
     * Binds a session to the task it just created. From this point the session
     * has a task and can never produce a second one — the app-visible half of
     * the parent plan's one-session-per-task invariant (§2.2).
     */
    suspend fun bindSession(sessionId: String, taskId: Long): Session = mutex.withLock {
        val existing = requireNotNull(sessions[sessionId]) { "No session " + sessionId }
        val bound = existing.copy(taskId = taskId, updatedAt = Instant.now())
        sessions[sessionId] = bound
        sessionByTask[taskId] = sessionId
        bound
    }


    suspend fun messages(sessionId: String): List<Message> =
        mutex.withLock { history[sessionId]?.toList().orEmpty() }

    suspend fun appendUser(sessionId: String, text: String): Message = mutex.withLock {
        val message = Message(
            id = nextMessageId++,
            role = MessageRole.User,
            text = text,
            createdAt = Instant.now(),
        )
        history.getOrPut(sessionId) { mutableListOf() } += message
        message
    }

    suspend fun appendAssistant(
        sessionId: String,
        text: String,
        components: List<AgentComponent>,
    ): Message = mutex.withLock {
        val message = Message(
            id = nextMessageId++,
            role = MessageRole.Assistant,
            text = text,
            components = components,
            createdAt = Instant.now(),
        )
        history.getOrPut(sessionId) { mutableListOf() } += message
        message
    }

    // --- Proposals ------------------------------------------------------------

    suspend fun recordProposal(
        sessionId: String,
        action: ProposalAction,
        summary: ProposalSummary,
        targetTaskId: Long?,
    ): String = mutex.withLock {
        val id = UUID.randomUUID().toString()
        proposals[id] = FakeProposal(id, sessionId, action, summary, targetTaskId)
        id
    }

    /** Executing a proposal is the only thing that writes a task (parent plan §2.4). */
    suspend fun confirmProposal(proposalId: String): Task = mutex.withLock {
        val proposal = requireNotNull(proposals[proposalId]) { "No proposal " + proposalId }
        val now = Instant.now()
        val result = when (proposal.action) {
            ProposalAction.Create -> {
                val id = nextTaskId++
                val summary = proposal.summary
                val dueDate = summary.dueDate ?: LocalDate.now(zone)
                val created = Task(
                    id = id,
                    title = summary.title,
                    description = summary.description,
                    dueAt = summary.dueAt ?: dueDate.atTime(9, 0).atZone(zone).toInstant(),
                    dueDate = dueDate,
                    isPriority = summary.isPriority,
                    recurrence = if (summary.recurrenceText != null) "FREQ=WEEKLY" else null,
                    recurrenceText = summary.recurrenceText,
                    nextFireAt = summary.dueAt,
                    status = TaskStatus.Active,
                    dueToday = dueDate == LocalDate.now(zone),
                    createdAt = now,
                    updatedAt = now,
                )
                tasks[id] = created
                created
            }

            ProposalAction.Update -> {
                val target = requireNotNull(tasks[proposal.targetTaskId]) { "No target task" }
                val summary = proposal.summary
                val dueDate = summary.dueDate ?: target.dueDate
                tasks[target.id] = target.copy(
                    title = summary.title.ifBlank { target.title },
                    description = summary.description.ifBlank { target.description },
                    dueAt = summary.dueAt ?: target.dueAt,
                    dueDate = dueDate,
                    isPriority = summary.isPriority,
                    // Rescheduling an `incomplete` task returns it to `active`.
                    // The scheduler treats incomplete -> active as a legal
                    // transition (parent plan §2.3); that re-openability is the
                    // entire reason the status exists.
                    status = if (target.status == TaskStatus.Incomplete) TaskStatus.Active else target.status,
                    dueToday = dueDate == LocalDate.now(zone),
                    updatedAt = now,
                )
                tasks.getValue(target.id)
            }

            ProposalAction.Cancel -> {
                val target = requireNotNull(tasks[proposal.targetTaskId]) { "No target task" }
                tasks[target.id] = target.copy(
                    status = TaskStatus.Cancelled,
                    cancelledAt = now,
                    updatedAt = now,
                    dueToday = false,
                )
                tasks.getValue(target.id)
            }

            ProposalAction.Complete -> {
                val target = requireNotNull(tasks[proposal.targetTaskId]) { "No target task" }
                tasks[target.id] = target.copy(
                    status = TaskStatus.Completed,
                    completedAt = now,
                    updatedAt = now,
                    dueToday = false,
                )
                tasks.getValue(target.id)
            }
        }
        resolve(proposalId, ProposalStatus.Confirmed)
        result
    }

    suspend fun rejectProposal(proposalId: String) = mutex.withLock {
        resolve(proposalId, ProposalStatus.Rejected)
    }

    suspend fun proposal(proposalId: String): FakeProposal? =
        mutex.withLock { proposals[proposalId] }

    suspend fun respond(sessionId: String, response: AgentResponse) {
        appendAssistant(sessionId, response.text, response.components)
    }

    /**
     * Rewrites the stored card in place so history shows what you agreed to
     * rather than a blank — the behaviour §5.3 asks for. Resolved cards stay
     * visible; they never vanish.
     */
    private fun resolve(proposalId: String, status: ProposalStatus) {
        val updated = proposals.getValue(proposalId).copy(status = status)
        proposals[proposalId] = updated
        val messages = history[updated.sessionId] ?: return
        for (index in messages.indices) {
            val message = messages[index]
            val touches = message.components.any {
                it is AgentComponent.Confirm && it.proposalId == proposalId
            }
            if (!touches) continue
            messages[index] = message.copy(
                components = message.components.map { component ->
                    if (component is AgentComponent.Confirm && component.proposalId == proposalId) {
                        component.copy(status = status)
                    } else {
                        component
                    }
                },
            )
        }
    }
}

data class FakeProposal(
    val id: String,
    val sessionId: String,
    val action: ProposalAction,
    val summary: ProposalSummary,
    val targetTaskId: Long?,
    val status: ProposalStatus = ProposalStatus.Pending,
)
