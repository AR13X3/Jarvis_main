package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.AgentResponse
import com.ar13x.jarvis.core.model.PagedMessages
import com.ar13x.jarvis.core.model.PagedSessions
import com.ar13x.jarvis.core.model.Session
import com.ar13x.jarvis.core.model.SessionKind
import com.ar13x.jarvis.core.model.Task

/**
 * The conversational half of the seam (plan §4.4).
 *
 * Note what is *absent*: the app never sends a tool list and never reasons about
 * one. Tool scoping is enforced server-side by session state (parent plan §2.3),
 * because a measured 5–25% violation rate makes prompt-level rules unusable as
 * enforcement. The app cannot offer the wrong action because the server never
 * offers the tool.
 */
interface AgentRepository {

    suspend fun createSession(kind: SessionKind, taskId: Long? = null): Session

    /** Session for a task, creating it on first open. One per task, by construction. */
    suspend fun sessionForTask(taskId: Long): Session

    /** Starts a *new* general conversation. */
    suspend fun newGeneralSession(): Session

    /**
     * Past general conversations, newest first (BUILD_NOTES §3.12).
     *
     * The Chat tab opens the most recent of these rather than starting blank,
     * so returning to it resumes where you were.
     */
    suspend fun generalSessions(page: Int = 1): PagedSessions

    /** Paged backwards through history via `before`. */
    suspend fun messages(sessionId: String, before: Long? = null, limit: Int = 30): PagedMessages

    suspend fun send(sessionId: String, text: String): AgentResponse

    /** Executes the proposal. This is the only thing that writes a task. */
    suspend fun confirmProposal(proposalId: String): Task

    suspend fun rejectProposal(proposalId: String)

    /** Pages the disambiguation buttons 3 at a time (plan §5.4). */
    suspend fun moreTaskOptions(sessionId: String, cursor: String): AgentComponent.TaskOptions
}
