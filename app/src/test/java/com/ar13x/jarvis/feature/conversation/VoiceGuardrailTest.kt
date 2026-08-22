package com.ar13x.jarvis.feature.conversation

import com.ar13x.jarvis.core.data.FakeAgentRepository
import com.ar13x.jarvis.core.data.FakeBackend
import com.ar13x.jarvis.core.data.FakeTaskRepository
import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.voice.VoiceController
import com.ar13x.jarvis.core.voice.VoiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * Voice must not become a second way to write.
 *
 * The parent plan deleted auto-confirm deliberately (§2.2): committing
 * something because the user did not look at their phone is wrong, and a
 * *misheard* commit is the same failure with a worse cause. So dictation ends
 * at the composer, and confirming stays a tap.
 *
 * These are the assertions that stop that eroding — the temptation to make
 * "yes" work by voice is real, and it would arrive as a small, reasonable-
 * looking change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceGuardrailTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A recogniser whose transcript the test decides. */
    private class FakeVoice : VoiceController {
        override val state = MutableStateFlow<VoiceState>(VoiceState.Idle)
        override val speaking = MutableStateFlow(false)
        private val _speakReplies = MutableStateFlow(false)
        override val speakReplies: Flow<Boolean> get() = _speakReplies

        val spoken = mutableListOf<String>()
        private var onFinal: ((String) -> Unit)? = null

        override fun available() = true

        override fun startListening(existingText: String, onFinal: (String) -> Unit) {
            this.onFinal = onFinal
            state.value = VoiceState.Listening()
        }

        /** What the recogniser would deliver when the user stops talking. */
        fun deliver(transcript: String) {
            state.value = VoiceState.Idle
            onFinal?.invoke(transcript)
        }

        override fun stopListening() { state.value = VoiceState.Idle }
        override fun cancelListening() { state.value = VoiceState.Idle }
        override fun clearFailure() = Unit
        override fun speak(text: String) { spoken += text }
        override fun stopSpeaking() { speaking.value = false }
        override suspend fun setSpeakReplies(enabled: Boolean) { _speakReplies.value = enabled }
    }

    private fun fixture(voice: VoiceController): Pair<ConversationViewModel, FakeBackend> {
        val backend = FakeBackend()
        val viewModel = ConversationViewModel(
            FakeAgentRepository(backend),
            FakeTaskRepository(backend),
            voice,
        )
        return viewModel to backend
    }

    @Test
    fun `a transcript lands in the composer and is not sent`() = runTest {
        val voice = FakeVoice()
        val (viewModel, _) = fixture(voice)
        viewModel.start(SessionTarget.General())
        advanceUntilIdle()

        viewModel.onEvent(ConversationEvent.ToggleMic)
        advanceUntilIdle()
        voice.deliver("remind me to call the dentist tomorrow at nine")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(
            "the transcript is a draft, not a message",
            "remind me to call the dentist tomorrow at nine",
            state.composerText,
        )
        assertTrue("nothing was sent", state.stream.isEmpty())
        assertFalse("and the agent was never asked", state.thinking)
    }

    /**
     * The one that matters. A proposal is the moment something gets written,
     * and no amount of talking may resolve one — only the button on the card.
     */
    @Test
    fun `speaking cannot confirm a proposal`() = runTest {
        val voice = FakeVoice()
        val (viewModel, backend) = fixture(voice)
        viewModel.start(SessionTarget.NewTask())
        advanceUntilIdle()

        // The fakes are seeded with fixtures, so this counts the delta rather
        // than asserting the table is empty.
        val before = backend.allTasks().size

        // The fake brain refuses to guess a date, so a proposal takes two
        // turns — the same multi-turn refinement §12.3 exercises.
        viewModel.onEvent(ConversationEvent.ComposerChanged("remind me to call the plumber"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()
        viewModel.onEvent(ConversationEvent.ComposerChanged("tomorrow at 9am"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()

        val proposals = viewModel.state.value.stream
            .flatMap { it.components }
            .filterIsInstance<AgentComponent.Confirm>()
        assertTrue("the fixture should have produced a card to confirm", proposals.isNotEmpty())

        // Say "yes" out loud, the way someone hands-free would expect to.
        viewModel.onEvent(ConversationEvent.ToggleMic)
        advanceUntilIdle()
        voice.deliver("yes")
        advanceUntilIdle()

        assertEquals(
            "a spoken yes must not create a task",
            before,
            backend.allTasks().size,
        )
        assertEquals("it is only ever a draft in the composer", "yes", viewModel.state.value.composerText)
    }

    @Test
    fun `replies are silent until spoken replies is turned on`() = runTest {
        val voice = FakeVoice()
        val (viewModel, _) = fixture(voice)
        viewModel.start(SessionTarget.General())
        advanceUntilIdle()

        viewModel.onEvent(ConversationEvent.ComposerChanged("what is due today"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()
        assertTrue("off by default — a task app must not start talking uninvited", voice.spoken.isEmpty())

        viewModel.onEvent(ConversationEvent.ToggleSpeakReplies)
        advanceUntilIdle()
        viewModel.onEvent(ConversationEvent.ComposerChanged("and tomorrow"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()
        assertTrue("and speaks once turned on", voice.spoken.isNotEmpty())
    }
}
