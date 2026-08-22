package com.ar13x.jarvis.core.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Everything the conversation needs from voice, behind one seam.
 *
 * Three collaborators — recogniser, speech engine, preference — are one concern
 * from the ViewModel's point of view, and injecting them separately meant every
 * test had to know about all three and supply an Android `Context` for each.
 * They are also the only Android-framework dependencies in a ViewModel that is
 * otherwise pure, which is exactly the kind of thing worth putting an interface
 * in front of.
 */
interface VoiceController {
    val state: StateFlow<VoiceState>
    val speaking: StateFlow<Boolean>
    val speakReplies: Flow<Boolean>

    /** False when the device has no recogniser — the mic is then hidden entirely. */
    fun available(): Boolean

    fun startListening(existingText: String, onFinal: (String) -> Unit)
    fun stopListening()
    fun cancelListening()
    fun clearFailure()

    fun speak(text: String)
    fun stopSpeaking()

    suspend fun setSpeakReplies(enabled: Boolean)
}

/** The real one. Wired by `VoiceModule`. */
class AndroidVoiceController @Inject constructor(
    private val input: VoiceInput,
    private val speaker: Speaker,
    private val settings: VoiceSettings,
) : VoiceController {

    override val state: StateFlow<VoiceState> get() = input.state
    override val speaking: StateFlow<Boolean> get() = speaker.speaking
    override val speakReplies: Flow<Boolean> get() = settings.speakReplies

    override fun available(): Boolean = input.isAvailable()

    override fun startListening(existingText: String, onFinal: (String) -> Unit) =
        input.start(existingText, onFinal)

    override fun stopListening() = input.stop()
    override fun cancelListening() = input.cancel()
    override fun clearFailure() = input.clearFailure()

    override fun speak(text: String) = speaker.speak(text)
    override fun stopSpeaking() = speaker.stop()

    override suspend fun setSpeakReplies(enabled: Boolean) = settings.setSpeakReplies(enabled)
}

/**
 * Voice, absent.
 *
 * Used by tests and by any caller with no Android context. Reports itself
 * unavailable, so the mic never renders and nothing is ever spoken — the same
 * behaviour as a device with no recognition service, which makes it an honest
 * stand-in rather than a special case.
 */
object NoVoice : VoiceController {
    override val state: StateFlow<VoiceState> = MutableStateFlow(VoiceState.Idle)
    override val speaking: StateFlow<Boolean> = MutableStateFlow(false)
    override val speakReplies: Flow<Boolean> = flowOf(false)

    override fun available(): Boolean = false

    override fun startListening(existingText: String, onFinal: (String) -> Unit) = Unit
    override fun stopListening() = Unit
    override fun cancelListening() = Unit
    override fun clearFailure() = Unit

    override fun speak(text: String) = Unit
    override fun stopSpeaking() = Unit

    override suspend fun setSpeakReplies(enabled: Boolean) = Unit
}
