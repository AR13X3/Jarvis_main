package com.ar13x.jarvis.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.BuildConfig
import com.ar13x.jarvis.core.data.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What About knows that `BuildConfig` cannot tell it.
 *
 * Separate from `UpdateViewModel` rather than bolted onto it: that one is also
 * driving the banner in the task list, and the gateway URL has nothing to do
 * with updates. Two small ViewModels on one screen is the honest shape.
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    tokenStore: TokenStore,
) : ViewModel() {

    /**
     * Where the app actually talks to — the paired URL, falling back to the
     * compiled-in default before pairing, which is exactly what
     * `GatewayUrlInterceptor` does. The two must agree or this screen becomes
     * a confident lie on the one screen whose job is answering "what is this
     * build doing".
     */
    val gatewayUrl: StateFlow<String> = tokenStore.gatewayUrl
        .map { it?.takeIf(String::isNotBlank) ?: BuildConfig.DEFAULT_GATEWAY_URL }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BuildConfig.DEFAULT_GATEWAY_URL,
        )
}
