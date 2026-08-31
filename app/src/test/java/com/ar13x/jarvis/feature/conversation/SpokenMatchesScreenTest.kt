package com.ar13x.jarvis.feature.conversation

import com.ar13x.jarvis.core.data.AgentRepository
import com.ar13x.jarvis.core.data.FakeAgentRepository
import com.ar13x.jarvis.core.data.FakeBackend
import com.ar13x.jarvis.core.data.FakeTaskRepository
import com.ar13x.jarvis.core.model.AgentResponse
import com.ar13x.jarvis.core.model.MessageRole
import com.ar13x.jarvis.core.voice.VoiceController
import com.ar13x.jarvis.core.voice.toUtterance
import com.ar13x.jarvis.core.voice.VoiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What is spoken must be what is on screen.
 *
 * The screen renders **persisted history** — the server owns truth, and that
 * design is right. Voice used to read the POST response body instead, so the
 * two agreed only as long as the server's copy matched what it returned.
 *
 * On 31 August it did not. An upstream 502 produced an apology in the response
 * that was never persisted, so the screen showed nothing at all. Spoken replies
 * happened to be off; had they been on, Jarvis would have said the apology out
 * loud to a blank screen. The app had the text and displayed none of it.
 *
 * The persistence bug is fixed on the gateway and there is a rendered fallback
 * now, but the *split* is what these tests pin: two sources of truth for one
 * turn, where one of them is audible and the other is visible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpokenMatchesScreenTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class RecordingVoice : VoiceController {
        override val state = MutableStateFlow<VoiceState>(VoiceState.Idle)
        override val speaking = MutableStateFlow(false)
        private val _speakReplies = MutableStateFlow(true)
        override val speakReplies: Flow<Boolean> get() = _speakReplies

        val spoken = mutableListOf<String>()

        override fun available() = true
        override fun startListening(existingText: String, onFinal: (String) -> Unit) = Unit
        override fun stopListening() = Unit
        override fun cancelListening() = Unit
        override fun clearFailure() = Unit
        override fun speak(text: String) { spoken += text }
        override fun stopSpeaking() { speaking.value = false }
        override suspend fun setSpeakReplies(enabled: Boolean) { _speakReplies.value = enabled }
    }

    /** Answers, and persists nothing. The shape of the 31 August failure. */
    private class AnswersButDoesNotPersist(
        delegate: AgentRepository,
    ) : AgentRepository by delegate {
        override suspend fun send(sessionId: String, text: String) =
            AgentResponse(text = "Sorry, something went wrong.")
    }

    private fun fixture(
        voice: VoiceController,
        wrap: (AgentRepository) -> AgentRepository = { it },
    ): ConversationViewModel {
        val backend = FakeBackend()
        return ConversationViewModel(
            wrap(FakeAgentRepository(backend)),
            FakeTaskRepository(backend),
            voice,
        )
    }

    /**
     * The regression. Nothing reached the screen, so nothing may reach the ear.
     */
    @Test
    fun `a turn the server never persisted is never spoken`() = runTest {
        val voice = RecordingVoice()
        val viewModel = fixture(voice) { AnswersButDoesNotPersist(it) }
        viewModel.start(SessionTarget.General())
        advanceUntilIdle()

        viewModel.onEvent(ConversationEvent.ComposerChanged("what is due today"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()

        val rendered = viewModel.state.value.stream
            .filter { it.role == MessageRole.Assistant }
            .map { it.text }

        assertTrue("the screen showed no reply: " + rendered, rendered.none { it.isNotBlank() })
        assertEquals("so nothing should have been spoken", emptyList<String>(), voice.spoken)
    }

    @Test
    fun `an ordinary reply is spoken, and it is the one on screen`() = runTest {
        val voice = RecordingVoice()
        val viewModel = fixture(voice)
        viewModel.start(SessionTarget.General())
        advanceUntilIdle()

        viewModel.onEvent(ConversationEvent.ComposerChanged("what is due today"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()

        val newest = viewModel.state.value.newestAssistantMessage()

        assertTrue("the fixture should have answered", newest != null)
        assertEquals(1, voice.spoken.size)
        // Spoken text is the rendered text with markdown stripped, so the
        // comparison is against the rendered turn rather than a literal.
        assertEquals(newest!!.toUtterance(), voice.spoken.single())
    }

    @Test
    fun `the same reply is not spoken twice when a later send adds nothing`() = runTest {
        val voice = RecordingVoice()
        val viewModel = fixture(voice)
        viewModel.start(SessionTarget.General())
        advanceUntilIdle()

        viewModel.onEvent(ConversationEvent.ComposerChanged("what is due today"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()
        val afterFirst = voice.spoken.size
        val firstUtterance = voice.spoken.lastOrNull()

        viewModel.onEvent(ConversationEvent.ComposerChanged("and this week"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()

        assertTrue("a second turn should have been spoken", voice.spoken.size > afterFirst)
        assertTrue(
            "and it should not be a repeat of the first",
            voice.spoken.last() != firstUtterance || firstUtterance == null,
        )
    }

    @Test
    fun `nothing is spoken when spoken replies are off`() = runTest {
        val voice = RecordingVoice()
        voice.setSpeakReplies(false)
        val viewModel = fixture(voice)
        viewModel.start(SessionTarget.General())
        advanceUntilIdle()

        viewModel.onEvent(ConversationEvent.ComposerChanged("what is due today"))
        viewModel.onEvent(ConversationEvent.Send)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), voice.spoken)
    }
}
