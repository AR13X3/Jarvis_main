package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.AgentResponse
import com.ar13x.jarvis.core.model.PagedMessages
import com.ar13x.jarvis.core.model.ProposalAction
import com.ar13x.jarvis.core.model.ProposalSummary
import com.ar13x.jarvis.core.model.Session
import com.ar13x.jarvis.core.model.SessionKind
import com.ar13x.jarvis.core.model.Task
import com.ar13x.jarvis.core.model.TaskOption
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scripted [AgentRepository] for phases A–C.
 *
 * What it is faithful about is the *contract*: structured responses, one
 * proposal per mutation, disambiguation three options at a time, and no tool
 * ever being offered on a terminal task. What it is not faithful about is
 * understanding — see [FakeAgentBrain].
 */
@Singleton
class FakeAgentRepository @Inject constructor(
    private val backend: FakeBackend,
) : AgentRepository {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val drafts = mutableMapOf<String, Draft>()
    private val optionPages = mutableMapOf<String, List<TaskOption>>()

    private val dayFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault())
    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    override suspend fun createSession(kind: SessionKind, taskId: Long?): Session =
        backend.createSession(kind, taskId)

    override suspend fun sessionForTask(taskId: Long): Session =
        backend.createSession(SessionKind.Task, taskId)

    override suspend fun generalSession(): Session =
        backend.createSession(SessionKind.General, null)

    override suspend fun messages(sessionId: String, before: Long?, limit: Int): PagedMessages {
        delay(FakeBackend.READ_LATENCY_MS)
        val all = backend.messages(sessionId)
        val older = if (before == null) all else all.filter { it.id < before }
        val window = older.takeLast(limit)
        return PagedMessages(messages = window, hasMore = window.size < older.size)
    }

    override suspend fun send(sessionId: String, text: String): AgentResponse {
        backend.appendUser(sessionId, text)
        delay(FakeBackend.AGENT_LATENCY_MS)

        val session = backend.session(sessionId)
        val boundTask = session?.taskId?.let { backend.task(it) }

        val response = when {
            boundTask != null -> respondBound(sessionId, boundTask, text)
            session?.kind == SessionKind.General -> respondGeneral(sessionId, text)
            else -> respondUnbound(sessionId, text)
        }
        backend.respond(sessionId, response)
        return response
    }

    override suspend fun confirmProposal(proposalId: String): Task {
        delay(FakeBackend.WRITE_LATENCY_MS)
        val proposal = backend.proposal(proposalId)
        val task = backend.confirmProposal(proposalId)
        // A created task claims the session that produced it, so the session is
        // bound from then on and can never create a second task — the invariant
        // the real schema enforces with `unique (task_id)` (parent plan §2.2).
        if (proposal != null && proposal.action == ProposalAction.Create) {
            backend.bindSession(proposal.sessionId, task.id)
            drafts.remove(proposal.sessionId)
        }
        return task
    }

    override suspend fun rejectProposal(proposalId: String) {
        delay(FakeBackend.WRITE_LATENCY_MS)
        val proposal = backend.proposal(proposalId)
        backend.rejectProposal(proposalId)
        // Rejecting clears the draft: the next turn starts over rather than
        // silently re-proposing the thing you just turned down.
        proposal?.let { drafts.remove(it.sessionId) }
    }

    override suspend fun moreTaskOptions(sessionId: String, cursor: String): AgentComponent.TaskOptions {
        delay(FakeBackend.READ_LATENCY_MS)
        return optionsComponent(optionPages.remove(cursor).orEmpty())
    }

    // --- Unbound session: the create flow ---------------------------------------

    /**
     * Multi-turn refinement (acceptance item 3): the fake will not guess a date,
     * so a vague request takes two turns to reach a proposal and nothing is
     * written until the card is confirmed.
     */
    private suspend fun respondUnbound(sessionId: String, text: String): AgentResponse {
        val draft = drafts.getOrPut(sessionId) { Draft() }.absorb(text)
        drafts[sessionId] = draft

        if (draft.title.isBlank()) {
            return AgentResponse(text = "Happy to set that up — what should I call it?")
        }
        if (draft.date == null) {
            return AgentResponse(
                text = "Got it: “" + draft.title + "”. When should that be due?",
            )
        }

        val dueAt = draft.instant()
        val summary = ProposalSummary(
            action = ProposalAction.Create,
            title = draft.title,
            dueAt = dueAt,
            dueDate = draft.date,
            isPriority = draft.isPriority,
            recurrenceText = draft.recurrenceText,
            description = "",
        )
        val proposalId = backend.recordProposal(sessionId, ProposalAction.Create, summary, null)
        return AgentResponse(
            text = "Just to confirm — " + quoted(draft.title) + ", " + describe(draft.date, dueAt) + ".",
            components = listOf(AgentComponent.Confirm(proposalId, summary)),
        )
    }

    // --- Bound session ----------------------------------------------------------

    private suspend fun respondBound(sessionId: String, task: Task, text: String): AgentResponse {
        // Terminal tasks are offered no mutation tool at all (parent plan §2.3).
        // The app does not enforce this and must not try to — it simply never
        // receives a proposal, and the composer is replaced by an explanatory
        // strip (plan §5.3).
        if (task.status.isTerminal) {
            return AgentResponse(text = describeTask(task))
        }

        val action = when {
            FakeAgentBrain.mentionsCompletion(text) -> ProposalAction.Complete
            FakeAgentBrain.mentionsCancellation(text) -> ProposalAction.Cancel
            FakeAgentBrain.mentionsReschedule(text) ||
                FakeAgentBrain.extractDate(text) != null -> ProposalAction.Update
            FakeAgentBrain.mentionsPriority(text) -> ProposalAction.Update
            else -> null
        } ?: return AgentResponse(text = describeTask(task))

        val newDate = FakeAgentBrain.extractDate(text)
        val newTime = FakeAgentBrain.extractTime(text)
            ?: task.dueAt.atZone(zone).toLocalTime()

        if (action == ProposalAction.Update && newDate == null && !FakeAgentBrain.mentionsPriority(text)) {
            return AgentResponse(text = "Sure — what should I move it to?")
        }

        val effectiveDate = newDate ?: task.dueDate
        val dueAt = effectiveDate.atTime(newTime).atZone(zone).toInstant()
        val summary = ProposalSummary(
            action = action,
            title = task.title,
            dueAt = dueAt,
            dueDate = effectiveDate,
            isPriority = if (FakeAgentBrain.mentionsPriority(text)) true else task.isPriority,
            recurrenceText = task.recurrenceText,
            description = task.description,
        )
        val proposalId = backend.recordProposal(sessionId, action, summary, task.id)

        val blurb = when (action) {
            ProposalAction.Complete -> "Mark " + quoted(task.title) + " as done?"
            ProposalAction.Cancel -> "Cancel " + quoted(task.title) + "?"
            ProposalAction.Update -> "Move " + quoted(task.title) + " to " + describe(effectiveDate, dueAt) + "?"
            ProposalAction.Create -> "" // unreachable in a bound session
        }
        return AgentResponse(
            text = blurb,
            components = listOf(AgentComponent.Confirm(proposalId, summary)),
        )
    }

    // --- General chat: the lookup flow -------------------------------------------

    /**
     * The §5.4 disambiguation flow: ask about a task, get asked for a date if it
     * is ambiguous, then pick from buttons three at a time.
     */
    private suspend fun respondGeneral(sessionId: String, text: String): AgentResponse {
        val terms = FakeAgentBrain.searchTerms(text)
        if (terms.isEmpty()) {
            return AgentResponse(text = "Ask me about a task and I will find it.")
        }

        val date = FakeAgentBrain.extractDate(text)
        var matches = backend.allTasks().filter { task ->
            terms.any { task.title.lowercase(Locale.ROOT).contains(it) }
        }
        if (date != null) {
            matches = matches.filter { it.dueDate == date }
        }

        if (matches.isEmpty()) {
            return AgentResponse(text = "I could not find a task matching that.")
        }

        // More than one candidate and nothing to separate them: ask for the date
        // rather than guessing. Exact timestamps are stored precisely so the date
        // can be the disambiguation key (parent plan §2.7).
        if (matches.size > 1 && date == null) {
            val titles = matches.map { it.title }.distinct()
            if (titles.size == 1) {
                return AgentResponse(
                    text = "I found " + matches.size + " tasks called " + quoted(titles.first()) +
                        ". Which date did you mean?",
                    components = listOf(optionsComponent(matches.map(::toOption))),
                )
            }
        }

        return AgentResponse(
            text = if (matches.size == 1) "Here it is." else "Here is what I found.",
            components = listOf(optionsComponent(matches.map(::toOption))),
        )
    }

    /** Buttons, three at a time; the rest hides behind a cursor (plan §5.4). */
    private fun optionsComponent(options: List<TaskOption>): AgentComponent.TaskOptions {
        val head = options.take(FakeBackend.OPTIONS_PER_PAGE)
        val tail = options.drop(FakeBackend.OPTIONS_PER_PAGE)
        val cursor = if (tail.isEmpty()) null else UUID.randomUUID().toString()
        if (cursor != null) optionPages[cursor] = tail
        return AgentComponent.TaskOptions(options = head, moreCursor = cursor)
    }

    private fun toOption(task: Task) =
        TaskOption(taskId = task.id, title = task.title, dueAt = task.dueAt, status = task.status)

    // --- Prose -------------------------------------------------------------------

    private fun describeTask(task: Task): String {
        val when_ = describe(task.dueDate, task.dueAt)
        val recurrence = task.recurrenceText?.let { " It repeats: " + it + "." }.orEmpty()
        return quoted(task.title) + " is " + task.status.name.lowercase(Locale.ROOT) +
            ", due " + when_ + "." + recurrence
    }

    private fun describe(date: LocalDate, instant: Instant): String =
        date.format(dayFormat) + ", " + instant.atZone(zone).toLocalTime().format(timeFormat)

    private fun quoted(value: String) = "“" + value + "”"

    /** Accumulates across turns, which is what makes refinement multi-turn. */
    private data class Draft(
        val title: String = "",
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val isPriority: Boolean = false,
        val recurrenceText: String? = null,
    ) {
        fun absorb(text: String): Draft {
            val extractedTitle = FakeAgentBrain.extractTitle(text)
            return copy(
                title = if (title.isNotBlank()) title else extractedTitle,
                date = FakeAgentBrain.extractDate(text) ?: date,
                time = FakeAgentBrain.extractTime(text) ?: time,
                isPriority = isPriority || FakeAgentBrain.mentionsPriority(text),
                recurrenceText = FakeAgentBrain.extractRecurrenceText(text) ?: recurrenceText,
            )
        }

        fun instant(): Instant {
            val day = requireNotNull(date)
            return day.atTime(time ?: LocalTime.of(9, 0))
                .atZone(ZoneId.systemDefault())
                .toInstant()
        }
    }
}
