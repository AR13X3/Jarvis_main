package com.ar13x.jarvis.core.voice

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reading the agent's replies aloud.
 *
 * Entirely on-device: `TextToSpeech` needs no permission, no manifest entry and
 * no network, so this works with Tailscale off and adds nothing to the gateway
 * contract — the app already has `AgentResponse.text` the moment a turn lands.
 *
 * Initialisation is asynchronous and can fail (no engine, no voice data for the
 * locale). Every failure path here ends in **silence, not an error**: a reply
 * that is shown but not spoken is a minor disappointment, and a dialog about a
 * missing TTS voice in the middle of a conversation is worse than the thing it
 * is reporting.
 */
@Singleton
class Speaker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var engine: TextToSpeech? = null

    @Volatile
    private var ready = false

    private val _speaking = MutableStateFlow(false)
    /** Drives the stop affordance — there must always be a way to shut it up. */
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    /**
     * Created lazily on first use rather than at app start.
     *
     * Binding a TTS engine costs a service connection and voice-data load, and
     * someone who never turns spoken replies on should never pay for it.
     */
    private fun ensureEngine(): TextToSpeech? {
        engine?.let { return it }
        val created = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                engine?.language = Locale.getDefault().takeIf { locale ->
                    engine?.isLanguageAvailable(locale) == TextToSpeech.LANG_AVAILABLE ||
                        engine?.isLanguageAvailable(locale) == TextToSpeech.LANG_COUNTRY_AVAILABLE
                } ?: Locale.ENGLISH
            }
        }
        created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _speaking.value = true }
            override fun onDone(utteranceId: String?) { _speaking.value = false }
            @Deprecated("Required by the abstract class", ReplaceWith(""))
            override fun onError(utteranceId: String?) { _speaking.value = false }
            override fun onError(utteranceId: String?, errorCode: Int) { _speaking.value = false }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { _speaking.value = false }
        })
        engine = created
        return created
    }

    /**
     * Speaks [text], replacing anything currently being spoken.
     *
     * `QUEUE_FLUSH`, deliberately: two agent turns in quick succession should
     * leave you hearing the *current* one, not the previous one followed by a
     * reply to a question you have already moved past.
     *
     * Silent when the phone is on silent. Someone who has silenced their phone
     * has stated a preference, and a task app is not the app that gets to
     * override it.
     */
    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (isSilenced()) return

        val tts = ensureEngine() ?: return
        if (!ready) return
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /** Barge-in: called when the user types, dictates, or leaves the screen. */
    fun stop() {
        engine?.stop()
        _speaking.value = false
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
        _speaking.value = false
    }

    private fun isSilenced(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
    }

    private companion object {
        const val UTTERANCE_ID = "jarvis.reply"
    }
}
