package com.ar13x.jarvis.feature.todos

import com.ar13x.jarvis.core.data.FakeTodoRepository
import com.ar13x.jarvis.core.model.TodoStatus
import com.ar13x.jarvis.core.ui.LoadState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The to-do list, through the ViewModel against the fake.
 *
 * The filters are what is worth asserting here. §5.2 makes the backlog a
 * *filter over one ordering* rather than a second collection, and a screen that
 * quietly diverged from that — by defaulting differently, or by treating an
 * empty status selection as "match nothing" — would look right and answer a
 * different question.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodoListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TodoListViewModel(FakeTodoRepository())

    private val LoadState<List<com.ar13x.jarvis.core.model.Todo>>.titles: List<String>
        get() = (this as LoadState.Ready).data.map { it.title }

    @Test
    fun `the default view is the unfinished work, not everything`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        // open + doing. A list that opened on everything would put finished
        // to-dos in front of Joy every time she looked.
        assertEquals(setOf(TodoStatus.Open, TodoStatus.Doing), vm.state.value.filters.statuses)
        assertFalse(vm.state.value.content.titles.contains("FCM measurement"))
        assertEquals(3, vm.state.value.content.titles.size)
    }

    @Test
    fun `the backlog is undated to-dos, not a separate collection`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleBacklog()
        advanceUntilIdle()

        val titles = vm.state.value.content.titles
        assertTrue(vm.state.value.filters.undatedOnly)
        // The dated one drops out; the undated ones stay. Same ordering, same
        // rows, one filter — which is what §5.2 asks for.
        assertFalse(titles.any { it.startsWith("Off-machine backup") })
        assertTrue(titles.contains("Place the uni assessment weeks"))
    }

    @Test
    fun `the backlog filter composes with the status filter rather than replacing it`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.toggleBacklog()
            advanceUntilIdle()
            vm.toggleStatus(TodoStatus.Doing)
            advanceUntilIdle()

            // Backlog + open only. If the backlog were a separate screen this
            // combination could not be expressed at all.
            assertTrue(vm.state.value.filters.undatedOnly)
            assertEquals(setOf(TodoStatus.Open), vm.state.value.filters.statuses)
            assertEquals(
                listOf("Place the uni assessment weeks", "Decide the tracked/scaffold split for every slot"),
                vm.state.value.content.titles,
            )
        }

    @Test
    fun `clearing every status shows everything rather than nothing`() = runTest(dispatcher) {
        // The trap. An empty selection in the UI means "no filter"; sending it
        // as `status=[]` would be a filter matching nothing, and the screen
        // would look like an empty database.
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleStatus(TodoStatus.Open)
        advanceUntilIdle()
        vm.toggleStatus(TodoStatus.Doing)
        advanceUntilIdle()

        assertTrue(vm.state.value.filters.statuses.isEmpty())
        assertEquals(4, vm.state.value.content.titles.size)
    }

    @Test
    fun `tapping a tag filters, tapping it again clears`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleTag("Uni")
        advanceUntilIdle()
        assertEquals(listOf("Place the uni assessment weeks"), vm.state.value.content.titles)

        vm.toggleTag("Uni")
        advanceUntilIdle()
        assertEquals(3, vm.state.value.content.titles.size)
    }

    @Test
    fun `the tag row does not lose a tag the current filter excluded`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val before = vm.state.value.tags

        vm.toggleTag("Uni")
        advanceUntilIdle()

        // Filtering to Uni leaves only Uni rows, so a tag row rebuilt from the
        // results would collapse to one chip — taking away the only way back.
        assertEquals(before, vm.state.value.tags)
        assertTrue("CBAI" in vm.state.value.tags)
    }

    @Test
    fun `advancing cycles open to doing to done and back to open`() = runTest(dispatcher) {
        // Tapping again undoes it eventually, which is what makes writing
        // without a confirmation defensible (§5.4).
        val vm = viewModel()
        advanceUntilIdle()
        // Show everything, so a row does not vanish out of the list as it moves.
        vm.toggleStatus(TodoStatus.Done)
        advanceUntilIdle()

        fun row() = (vm.state.value.content as LoadState.Ready).data.first { it.todoId == 1L }

        assertEquals(TodoStatus.Open, row().status)

        vm.advance(row()); advanceUntilIdle()
        assertEquals(TodoStatus.Doing, row().status)

        vm.advance(row()); advanceUntilIdle()
        assertEquals(TodoStatus.Done, row().status)

        vm.advance(row()); advanceUntilIdle()
        assertEquals(TodoStatus.Open, row().status)
    }

    @Test
    fun `a created to-do appears immediately, even under a filter that excludes it`() =
        runTest(dispatcher) {
            // The feedback that matters: someone just typed it, so it has to be
            // visible. A reload would drop it straight back out whenever the
            // current filter excludes it, which reads as the add having failed.
            val vm = viewModel()
            advanceUntilIdle()
            vm.toggleBacklog()
            advanceUntilIdle()
            vm.toggleTag("Uni")
            advanceUntilIdle()

            vm.create("Ask gw03 about the weekday base", "", listOf("Reskill"))
            advanceUntilIdle()

            assertEquals("Ask gw03 about the weekday base", vm.state.value.content.titles.first())
        }

    @Test
    fun `a new tag joins the filter row`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse("Speedway" in vm.state.value.tags)

        vm.create("Book the Friday shift swap", "", listOf("Speedway"))
        advanceUntilIdle()

        assertTrue("Speedway" in vm.state.value.tags)
    }

    @Test
    fun `a created to-do is undated, which is what the backlog is`() = runTest(dispatcher) {
        // The capture sheet has no date field on purpose (§5.2): an undated
        // to-do is the ordinary case, not a half-finished one.
        val vm = viewModel()
        advanceUntilIdle()

        vm.create("Place the CBAI review", "", emptyList())
        advanceUntilIdle()

        val created = (vm.state.value.content as LoadState.Ready).data.first()
        assertTrue(created.isUndated)
        assertFalse(created.hasReminders)
    }

    @Test
    fun `advancing never reaches cancelled`() = runTest(dispatcher) {
        // Cancelling is a decision, not a step, and it must not be reachable by
        // tapping past `done`. It lives on the detail screen.
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleStatus(TodoStatus.Done)
        advanceUntilIdle()

        fun row() = (vm.state.value.content as LoadState.Ready).data.first { it.todoId == 1L }

        repeat(6) {
            vm.advance(row())
            advanceUntilIdle()
            assertFalse(row().status == TodoStatus.Cancelled)
        }
    }
}
