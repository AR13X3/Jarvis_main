package com.ar13x.jarvis.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.core.data.DashboardRepository
import com.ar13x.jarvis.core.data.toFailureReason
import com.ar13x.jarvis.core.ui.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * The dashboard (v2 plan §6).
 *
 * Deliberately thin. Everything the screen needs beyond the response is on
 * [DashboardView], which is pure and tested directly — a ViewModel is a poor
 * place for rules you want to assert, and the four rules here are the ones most
 * worth asserting.
 *
 * No window is requested. Omitting the dates takes the gateway's own default,
 * because "this period" is a definition the server owns; picking one here would
 * be the same mistake as an app-side drift threshold, and it would make the
 * screen disagree with the summaries about what a week is.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DashboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<LoadState<DashboardView>>(LoadState.Loading)
    val state: StateFlow<LoadState<DashboardView>> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = LoadState.Loading
            _state.value = try {
                LoadState.Ready(
                    DashboardView(
                        dashboard = repository.dashboard(),
                        // Read once per load rather than per recomposition. It
                        // decides one thing — whether the window is historical —
                        // and a value that changed mid-frame would let the stale
                        // caption appear and vanish across midnight while the
                        // numbers behind it stayed put.
                        today = LocalDate.now(),
                    ),
                )
            } catch (e: Exception) {
                LoadState.Failed(e.toFailureReason())
            }
        }
    }
}
