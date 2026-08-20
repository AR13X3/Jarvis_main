package com.ar13x.jarvis.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.core.data.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Decides whether the app opens on onboarding or on the Tasks tab.
 *
 * Starts as `null` rather than `false` so the first frame can render nothing
 * instead of the token screen. Guessing "no token" for the few milliseconds
 * DataStore takes to answer would flash onboarding at every existing user on
 * every cold start.
 */
@HiltViewModel
class AppGateViewModel @Inject constructor(
    tokenStore: TokenStore,
) : ViewModel() {

    val hasToken: StateFlow<Boolean?> = tokenStore.token
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
