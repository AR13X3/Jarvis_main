package com.ar13x.jarvis.feature.conversation

import com.ar13x.jarvis.core.data.FakeAgentRepository
import com.ar13x.jarvis.core.data.FakeBackend
import com.ar13x.jarvis.core.data.FakeTaskRepository
import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.CancelScope
import com.ar13x.jarvis.core.model.Message
import com.ar13x.jarvis.core.model.ProposalStatus
import com.ar13x.jarvis.core.model.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The app-side slice of the acceptance list (plan §12), exercised through the
 * ViewModel against the fakes.
 *
 * Testing the ViewModel rather than the fake repository is the point: the fake
 * stands in for the gateway, so asserting on it only proves the stand-in works.
 * What needs proving is that the *app* writes nothing until a card is confirmed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun fixture(): Triple<ConversationViewModel, FakeBackend, FakeTaskRepository> {
        val backend = FakeBackend()
        val taskRepository = FakeTaskRepository(backend)
        val viewModel = ConversationViewModel(FakeAgentRepository(backend), taskRepository)
        return Triple(viewModel, backend, taskRepository)
    }

    private fun ConversationUiState.confirmCards(): List<AgentComponent.Confirm> =
        stream.flatMap { it.components }.filterIsInstance<AgentComponent.Confirm>()

    private fun ConversationUiState.optionCards(): List<AgentComponent.TaskOptions> =
        stream.flatMap { it.components }.filterIsInstance<AgentComponent.TaskOptions>()

    // --- §12.3 — multi-turn refinement, nothing written until confirmation ----

    @Test
    fun `a vague request asks a question instead of guessing a date`() = runTest {
        val (viewModel, backend, _) = fixture()
        viewModel.start(SessionTarget.NewTask())
        advanceUntilIdle()

        val before = backend.allTasks().size

        viewModel.onEvent(ConversationEvent.ComposerChanged("remind me to call the plumber"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("no proposal should exist yet", state.confirmCards().isEmpty())
        assertTrue("the agent should have asked something", state.stream.any { it.text.isNotBlank() })
        assertEquals("nothing may be written before confirmation", before, backend.allTasks().size)
    }

    @Test
    fun `supplying the date produces a proposal, and still writes nothing`() = runTest {
        val (viewModel, backend, _) = fixture()
        viewModel.start(SessionTarget.NewTask())
        advanceUntilIdle()
        val before = backend.allTasks().size

        viewModel.onEvent(ConversationEvent.ComposerChanged("remind me to call the plumber"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()

        viewModel.onEvent(ConversationEvent.ComposerChanged("tomorrow at 9am"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()

        val card = viewModel.state.value.confirmCards().single()
        assertEquals(ProposalStatus.Pending, card.status)
        assertEquals("a pending proposal is not a task", before, backend.allTasks().size)
    }

    // --- §12.4 — reject writes nothing ---------------------------------------

    @Test
    fun `rejecting a proposal creates no task and leaves the card resolved`() = runTest {
        val (viewModel, backend, _) = fixture()
        viewModel.start(SessionTarget.NewTask())
        advanceUntilIdle()
        val before = backend.allTasks().size

        viewModel.onEvent(ConversationEvent.ComposerChanged("remind me to call the plumber tomorrow at 9am"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()

        val proposalId = viewModel.state.value.confirmCards().single().proposalId
        viewModel.onEvent(ConversationEvent.Reject(proposalId))
        advanceUntilIdle()

        assertEquals("reject must not write", before, backend.allTasks().size)

        // The card stays in history in its resolved state — scrolling back
        // should show what you turned down, not a blank (plan §5.3).
        val card = viewModel.state.value.confirmCards().single()
        assertEquals(ProposalStatus.Rejected, card.status)
    }

    @Test
    fun `confirming a proposal writes exactly one task and binds the session`() = runTest {
        val (viewModel, backend, _) = fixture()
        viewModel.start(SessionTarget.NewTask())
        advanceUntilIdle()
        val before = backend.allTasks().size

        viewModel.onEvent(ConversationEvent.ComposerChanged("remind me to call the plumber tomorrow at 9am"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()

        val proposalId = viewModel.state.value.confirmCards().single().proposalId
        viewModel.onEvent(ConversationEvent.Confirm(proposalId))
        advanceUntilIdle()

        assertEquals(before + 1, backend.allTasks().size)

        val state = viewModel.state.value
        assertEquals(ProposalStatus.Confirmed, state.confirmCards().single().status)
        // §12.1's app-side half: the session now has a task, so the UI can never
        // offer a second create — the server simply stops offering the tool.
        assertTrue("session should be bound after a create", state.task != null)
    }

    // --- the general-chat handoff --------------------------------------------

    /**
     * A seeded session sends on open. The user already said what they wanted in
     * general chat; making them retype it because the server scopes tools per
     * session would be the app leaking its own architecture at them.
     */
    @Test
    fun `a seeded new task session sends the seed without being asked`() = runTest {
        val (viewModel, backend, _) = fixture()
        val before = backend.allTasks().size

        viewModel.start(SessionTarget.NewTask(seed = "remind me to go to the gym tomorrow at 11pm"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(
            "the seed should already be in the transcript",
            state.stream.any { it.text.contains("gym") },
        )
        assertEquals("the composer must not still hold it", "", state.composerText)
        // It went far enough to produce a proposal, which is the whole point of
        // handing over to a session that can create.
        assertTrue(state.confirmCards().isNotEmpty())
        // Seeding skips the typing, not the confirmation. A handoff that wrote
        // straight through would be the one place in the app where something
        // reached the database without the user agreeing to it.
        assertEquals("still nothing written until confirmed", before, backend.allTasks().size)
    }

    @Test
    fun `an unseeded new task session sends nothing`() = runTest {
        val (viewModel, _, _) = fixture()

        viewModel.start(SessionTarget.NewTask())
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("nothing should have been sent", state.stream.isEmpty())
        assertFalse(state.thinking)
    }

    // --- §12.2 — a terminal task's session shows no composer ------------------

    @Test
    fun `a completed task session is read-only`() = runTest {
        val (viewModel, backend, _) = fixture()
        val completed = backend.allTasks().first { it.status == TaskStatus.Completed }

        viewModel.start(SessionTarget.Bound(completed.id))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("terminal tasks are read-only", state.isReadOnly)
        assertFalse("the composer must not be sendable", state.canSend)
    }

    @Test
    fun `a cancelled task session is read-only, an incomplete one is not`() = runTest {
        val (viewModel, backend, taskRepository) = fixture()

        val active = backend.allTasks().first { it.status == TaskStatus.Active && !it.isRecurring }
        taskRepository.cancel(active.id, CancelScope.Series)
        viewModel.start(SessionTarget.Bound(active.id))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isReadOnly)

        // `incomplete` is a lapse, not a resolution: it keeps its full mutation
        // set and is the one you most want to reschedule (plan §4.3).
        val (other, backend2, _) = fixture()
        val lapsed = backend2.allTasks().first { it.status == TaskStatus.Incomplete }
        other.start(SessionTarget.Bound(lapsed.id))
        advanceUntilIdle()
        assertFalse("incomplete must stay mutable", other.state.value.isReadOnly)
    }

    // --- §12.7 — disambiguation, three at a time ------------------------------

    @Test
    fun `ambiguous lookup offers three options and pages the rest`() = runTest {
        val (viewModel, _, _) = fixture()
        viewModel.start(SessionTarget.General)
        advanceUntilIdle()

        viewModel.onEvent(ConversationEvent.ComposerChanged("dinner with sam"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()

        val card = viewModel.state.value.optionCards().single()
        assertEquals("buttons come three at a time", 3, card.options.size)
    }

    // --- Optimistic send ------------------------------------------------------

    @Test
    fun `the users message appears before the agent replies, then is replaced`() = runTest {
        val (viewModel, _, _) = fixture()
        viewModel.start(SessionTarget.General)
        advanceUntilIdle()

        viewModel.onEvent(ConversationEvent.ComposerChanged("what is due today"))
        viewModel.onEvent(ConversationEvent.Send)

        // Before the scheduler runs anything, the message is already on screen
        // and the composer is empty — that is the whole point of optimistic send.
        val mid = viewModel.state.value
        assertEquals("", mid.composerText)
        assertTrue(mid.thinking)
        assertEquals("what is due today", mid.optimistic?.text)

        advanceUntilIdle()

        val settled = viewModel.state.value
        assertNull("the optimistic copy is dropped once the server has it", settled.optimistic)
        assertFalse(settled.thinking)
        assertTrue(settled.stream.any { it.text == "what is due today" })
        // Exactly once — a duplicated turn would mean the optimistic message was
        // cleared on a different frame from the history landing.
        assertEquals(1, settled.stream.count { it.text == "what is due today" })
    }

    @Test
    fun `stream ids are unique so the lazy list cannot crash on duplicate keys`() = runTest {
        val (viewModel, _, _) = fixture()
        viewModel.start(SessionTarget.General)
        advanceUntilIdle()

        viewModel.onEvent(ConversationEvent.ComposerChanged("hello"))
        viewModel.onEvent(ConversationEvent.Send)

        val ids = viewModel.state.value.stream.map(Message::id)
        assertEquals(ids.size, ids.distinct().size)
    }
}
