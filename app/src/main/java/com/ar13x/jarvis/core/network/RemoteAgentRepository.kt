package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.data.AgentRepository
import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.AgentResponse
import com.ar13x.jarvis.core.model.PagedMessages
import com.ar13x.jarvis.core.model.PagedSessions
import com.ar13x.jarvis.core.model.Session
import com.ar13x.jarvis.core.model.SessionKind
import com.ar13x.jarvis.core.model.Task
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteAgentRepository @Inject constructor(
    private val api: JarvisApi,
    private val json: Json,
) : AgentRepository {

    override suspend fun createSession(kind: SessionKind, taskId: Long?): Session =
        gatewayCall(mutating = true) {
            api.createSession(CreateSessionBody(kind = kind.wireName(), taskId = taskId))
        }

    /**
     * `POST /sessions` with a `task_id` is idempotent server-side: the unique
     * constraint on `agent.sessions.task_id` means the gateway returns the
     * existing session rather than making a second one (parent plan §2.2). So
     * opening a task repeatedly does not accumulate sessions.
     */
    override suspend fun sessionForTask(taskId: Long): Session =
        createSession(SessionKind.Task, taskId)

    override suspend fun newGeneralSession(): Session =
        createSession(SessionKind.General, null)

    override suspend fun generalSessions(page: Int): PagedSessions =
        gatewayCall { api.sessions(kind = SessionKind.General.wireName(), page = page) }

    override suspend fun messages(sessionId: String, before: Long?, limit: Int): PagedMessages =
        gatewayCall { api.messages(sessionId, before, limit) }

    /**
     * The one call where a timeout is genuinely ambiguous: the turn may have
     * reached the model and been persisted even though no response came back.
     * `mutating = true` is what makes §8.2 say "your message was sent — check
     * the session" rather than offering a retry that would duplicate it.
     */
    override suspend fun send(sessionId: String, text: String): AgentResponse =
        gatewayCall(mutating = true) { api.sendMessage(sessionId, SendMessageBody(text)) }

    override suspend fun confirmProposal(proposalId: String): Task =
        gatewayCall(mutating = true) { api.confirmProposal(proposalId).readTask(json) }

    override suspend fun rejectProposal(proposalId: String) {
        gatewayCall(mutating = true) { api.rejectProposal(proposalId) }
    }

    /**
     * Pages a disambiguation card (plan §5.4).
     *
     * A dedicated route, so it costs no model call and invents no user turn —
     * which is what made redeeming the cursor through `POST /messages`
     * unacceptable.
     */
    override suspend fun moreTaskOptions(sessionId: String, cursor: String): AgentComponent.TaskOptions =
        gatewayCall { api.options(sessionId, cursor) }
}

internal fun SessionKind.wireName(): String = when (this) {
    SessionKind.Task -> "task"
    SessionKind.General -> "general"
}
