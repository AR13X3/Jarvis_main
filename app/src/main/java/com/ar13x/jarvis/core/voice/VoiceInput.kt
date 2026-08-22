package com.ar13x.jarvis.core.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * What the microphone is currently doing.
 *
 * A state, not a pair of booleans: "listening" and "there is a partial
 * transcript" are not independent, and the composer needs to render exactly one
 * of these at a time.
 */
sealed interface VoiceState {
    data object Idle : VoiceState

    /**
     * Listening. [partial] is the running transcript, [amplitude] the smoothed
     * input level in 0..1.
     *
     * Partials matter: they are the difference between dictation that looks
     * alive and a button that appears to do nothing for four seconds. They are
     * also why this uses `SpeechRecognizer` rather than
     * `ACTION_RECOGNIZE_SPEECH`, which would put Google's own dialog over a
     * screen that §6 spends its whole length making look deliberate.
     */
    data class Listening(val partial: String = "", val amplitude: Float = 0f) : VoiceState

    /** Recognition failed. Shown briefly in the composer, never as a dialog. */
    data class Failed(val message: String) : VoiceState
}

/**
 * Dictation into the composer (app-side only — the gateway knows nothing about
 * it and needs no change).
 *
 * On-device recognition is preferred where the device offers it: `minSdk = 31`
 * already clears `createOnDeviceSpeechRecognizer`, it works with no network at
 * all, and nothing spoken leaves the phone. It falls back to the ordinary
 * recogniser when no on-device model is installed, which is a real possibility
 * on a device that has never downloaded one.
 *
 * **Not** a hands-free loop. The transcript lands in the composer and the user
 * presses send. Nothing here can confirm a proposal — that stays a tap, because
 * a misheard yes would write a task, which is precisely what parent plan §2.2
 * deleted auto-confirm to prevent.
 */
class VoiceInput @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    /** Text captured before this utterance, so dictation appends rather than replaces. */
    private var prefix: String = ""

    /** Final transcript handed back when the utterance completes. */
    private var onResult: ((String) -> Unit)? = null

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether dictation can work at all on this device.
     *
     * Checked rather than assumed: with no recognition service present the app
     * would show a mic button that silently fails, which is worse than showing
     * no mic button.
     */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Must be called on the main thread — `SpeechRecognizer` requires it and
     * throws otherwise.
     */
    fun start(existingText: String, onFinal: (String) -> Unit) {
        if (!hasPermission() || !isAvailable()) return
        release()

        prefix = existingText.trimEnd()
        onResult = onFinal

        val created = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceState.Listening()
            }

            override fun onRmsChanged(rmsdB: Float) {
                // The API reports roughly -2..10 dB. Normalised here so the
                // composer animates against a stable 0..1 rather than
                // re-deriving the range at the call site.
                val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                val current = _state.value
                if (current is VoiceState.Listening) {
                    _state.value = current.copy(amplitude = level)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults.firstTranscript() ?: return
                val current = _state.value
                if (current is VoiceState.Listening) {
                    _state.value = current.copy(partial = joined(text))
                }
            }

            override fun onResults(results: Bundle?) {
                val text = results.firstTranscript()
                _state.value = VoiceState.Idle
                if (!text.isNullOrBlank()) onResult?.invoke(joined(text))
                release()
            }

            override fun onError(error: Int) {
                // No speech is not a failure worth reporting — it is what
                // happens when someone taps the mic and changes their mind.
                _state.value = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    -> VoiceState.Idle

                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        VoiceState.Failed("Jarvis needs microphone access to do that.")

                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    -> VoiceState.Failed("Speech recognition needs a connection right now.")

                    else -> VoiceState.Failed("Didn't catch that.")
                }
                release()
            }

            override fun onEndOfSpeech() = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        recognizer = created
        created.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Only meaningful for the networked recogniser; harmless on the
                // on-device one, which is already offline by definition.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            },
        )
    }

    /** Stops listening and keeps whatever was transcribed so far. */
    fun stop() {
        recognizer?.stopListening()
        if (_state.value is VoiceState.Listening) _state.value = VoiceState.Idle
    }

    /** Abandons the utterance entirely. */
    fun cancel() {
        recognizer?.cancel()
        release()
        _state.value = VoiceState.Idle
    }

    fun clearFailure() {
        if (_state.value is VoiceState.Failed) _state.value = VoiceState.Idle
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    /** Dictation appends to what is already typed rather than wiping it. */
    private fun joined(text: String): String =
        if (prefix.isEmpty()) text else prefix + " " + text

    private fun Bundle?.firstTranscript(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
