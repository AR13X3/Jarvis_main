package com.ar13x.jarvis.feature.conversation

import com.ar13x.jarvis.core.data.FakeAgentRepository
import com.ar13x.jarvis.core.data.FakeBackend
import com.ar13x.jarvis.core.data.FakeTaskRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Answering the agent's overdue question (docs/joy-to-gw03-07).
 *
 * The point of interest is the cap. The app deliberately does **not** enforce
 * it — the count is the gateway's, and a device-held one would reset on
 * reinstall — so what these prove is that the app spends allowances one at a
 * time and surfaces exhaustion as a failure rather than swallowing it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverdueAnswerTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun fixture(): Triple<ConversationViewModel, FakeBackend, Long> {
        val backend = FakeBackend()
        val taskId = backend.allTasks().first { !it.status.isTerminal }.id
        backend.registerOccurrence(OCCURRENCE, taskId)
        val viewModel = ConversationViewModel(
            FakeAgentRepository(backend),
            FakeTaskRepository(backend),
        )
        return Triple(viewModel, backend, taskId)
    }

    @Test
    fun `saying it is done completes the task`() = runTest {
        val (viewModel, backend, taskId) = fixture()
        viewModel.start(SessionTarget.Bound(taskId))
        advanceUntilIdle()

        viewModel.onEvent(ConversationEvent.CompleteOccurrence(OCCURRENCE))
        advanceUntilIdle()

        assertEquals(TaskStatus.Completed, backend.task(taskId)!!.status)
    }

    @Test
    fun `each push back spends exactly one allowance`() = runTest {
        val (viewModel, backend, taskId) = fixture()
        viewModel.start(SessionTarget.Bound(taskId))
        advanceUntilIdle()

        viewModel.onEvent(ConversationEvent.ExtendOccurrence(OCCURRENCE, 15))
        advanceUntilIdle()
        assertEquals(1, backend.extensionsUsed(OCCURRENCE))

        viewModel.onEvent(ConversationEvent.ExtendOccurrence(OCCURRENCE, 30))
        advanceUntilIdle()
        assertEquals(2, backend.extensionsUsed(OCCURRENCE))
    }

    @Test
    fun `pushing back moves the deadline later`() = runTest {
        val (viewModel, backend, taskId) = fixture()
        viewModel.start(SessionTarget.Bound(taskId))
        advanceUntilIdle()
        val before = backend.task(taskId)!!.dueAt

        viewModel.onEvent(ConversationEvent.ExtendOccurrence(OCCURRENCE, 60))
        advanceUntilIdle()

        assertTrue("the new deadline must be later", backend.task(taskId)!!.dueAt.isAfter(before))
    }

    /**
     * The third attempt. The gateway refuses, and the app has to *show* that
     * rather than absorb it — a push the user believes happened and did not is
     * worse than one that visibly failed.
     */
    @Test
    fun `a third push back fails visibly and spends nothing`() = runTest {
        val (viewModel, backend, taskId) = fixture()
        viewModel.start(SessionTarget.Bound(taskId))
        advanceUntilIdle()

        repeat(2) {
            viewModel.onEvent(ConversationEvent.ExtendOccurrence(OCCURRENCE, 15))
            advanceUntilIdle()
        }
        val deadlineAfterTwo = backend.task(taskId)!!.dueAt

        viewModel.onEvent(ConversationEvent.ExtendOccurrence(OCCURRENCE, 15))
        advanceUntilIdle()

        assertEquals("no third allowance", 2, backend.extensionsUsed(OCCURRENCE))
        assertEquals("and the deadline did not move", deadlineAfterTwo, backend.task(taskId)!!.dueAt)
        assertNotNull(
            "the refusal must reach the user",
            viewModel.state.value.transientFailure,
        )
    }

    private companion object {
        const val OCCURRENCE = 9001L
    }
}
