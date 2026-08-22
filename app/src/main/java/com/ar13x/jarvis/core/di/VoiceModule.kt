package com.ar13x.jarvis.core.di

import com.ar13x.jarvis.core.voice.AndroidVoiceController
import com.ar13x.jarvis.core.voice.VoiceController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

/**
 * Voice is **per ViewModel**, not app-wide.
 *
 * The recogniser holds a live `SpeechRecognizer` bound to one utterance and one
 * callback. Shared across the Chat tab and an open task session, whichever
 * screen tapped the mic last would own the callback while both rendered as
 * listening — so a transcript could land in the conversation you were not
 * looking at.
 *
 * The speech engine underneath is still a singleton; only the coordination is
 * scoped, which is the right split — one voice, several places that can use it.
 */
@Module
@InstallIn(ViewModelComponent::class)
abstract class VoiceModule {

    @Binds
    @ViewModelScoped
    abstract fun bindVoiceController(impl: AndroidVoiceController): VoiceController
}
