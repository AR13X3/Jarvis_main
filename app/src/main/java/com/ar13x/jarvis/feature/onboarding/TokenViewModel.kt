package com.ar13x.jarvis.feature.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.BuildConfig
import com.ar13x.jarvis.core.data.TokenStore
import com.ar13x.jarvis.core.model.FailureReason
import com.ar13x.jarvis.core.model.JarvisException
import com.ar13x.jarvis.core.network.JarvisApi
import com.ar13x.jarvis.core.network.gatewayCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class TokenUiState(
    val url: String = BuildConfig.DEFAULT_GATEWAY_URL,
    val token: String = "",
    val probing: Boolean = false,
    /** Set when the probe reached the gateway but the token was refused. */
    val failure: FailureReason? = null,
    val diagnosis: String? = null,
    val saved: Boolean = false,
) {
    val canSubmit: Boolean get() = token.isNotBlank() && url.isNotBlank() && !probing
}

/**
 * First run (plan §5.5): gateway URL pre-filled, token entered once, then a
 * connectivity probe with a real diagnosis on failure.
 *
 * The probe is two calls on purpose. `/health` needs no token, so reaching it
 * proves the tailnet and TLS are fine; only then does an authenticated call
 * decide whether the *token* is wrong. Collapsing them into one request would
 * make "Tailscale is off" and "you mistyped the token" produce the same
 * message, which is the exact failure §8.2 exists to prevent.
 */
@HiltViewModel
class TokenViewModel @Inject constructor(
    private val api: JarvisApi,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TokenUiState())
    val state: StateFlow<TokenUiState> = _state.asStateFlow()

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, diagnosis = null) }

    fun onTokenChange(value: String) = _state.update { it.copy(token = value, diagnosis = null) }

    fun connect() {
        val current = _state.value
        if (!current.canSubmit) return

        viewModelScope.launch {
            _state.update { it.copy(probing = true, diagnosis = null, failure = null) }

            // Step 1 — can we reach it at all? Unauthenticated on purpose.
            val reachable = runCatching { gatewayCall { api.health() } }
            if (reachable.isFailure) {
                _state.update {
                    it.copy(
                        probing = false,
                        diagnosis = "Can't reach Jarvis at that address. " +
                            "Is Tailscale connected on this phone?",
                    )
                }
                return@launch
            }

            // Step 2 — save, then make one authenticated call. The token has to
            // be stored first because the interceptor reads it from the store,
            // not from this screen; a rejected token is cleared again below.
            tokenStore.save(current.token.trim(), current.url.trim())

            val authorised = runCatching { gatewayCall { api.sections() } }
            if (authorised.isFailure) {
                val reason = (authorised.exceptionOrNull() as? JarvisException)?.reason
                tokenStore.clear()
                _state.update {
                    it.copy(
                        probing = false,
                        failure = reason,
                        diagnosis = if (reason == FailureReason.Unauthorised) {
                            "Jarvis is there, but that token was refused. Check it and try again."
                        } else {
                            "Reached Jarvis, but the first request failed. Try again in a moment."
                        },
                    )
                }
                return@launch
            }

            _state.update { it.copy(probing = false, saved = true) }
        }
    }
}
