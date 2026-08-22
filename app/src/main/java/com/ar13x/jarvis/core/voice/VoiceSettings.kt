package com.ar13x.jarvis.core.voice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.voice: DataStore<Preferences> by preferencesDataStore("voice")

/**
 * Whether replies are read aloud.
 *
 * **Off by default.** A task app that starts talking the first time it is
 * opened — possibly in an office, possibly on a bus — has made a decision that
 * was not its to make. Speaking is opt-in, and the toggle sits in the
 * conversation header where the consequence is visible.
 *
 * Dictation needs no setting: it only ever happens because someone pressed the
 * microphone.
 */
@Singleton
class VoiceSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        val SPEAK_REPLIES = booleanPreferencesKey("speak_replies")
    }

    val speakReplies: Flow<Boolean> = context.voice.data.map { it[SPEAK_REPLIES] ?: false }

    suspend fun setSpeakReplies(enabled: Boolean) {
        context.voice.edit { it[SPEAK_REPLIES] = enabled }
    }
}
