package com.ar13x.jarvis.feature.summaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.core.data.SummaryRepository
import com.ar13x.jarvis.core.data.toFailureReason
import com.ar13x.jarvis.core.model.FailureReason
import com.ar13x.jarvis.core.model.Summary
import com.ar13x.jarvis.core.model.SummaryPeriod
import com.ar13x.jarvis.core.ui.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummariesUiState(
    val content: LoadState<List<Summary>> = LoadState.Loading,
    /** `null` shows both dailies and weeklies together, newest first. */
    val period: SummaryPeriod? = null,
    /**
     * A generation in flight.
     *
     * Held separately from [content] because the list stays readable while one
     * is being written — it costs a model call and takes seconds, and blanking
     * the screen for it would hide the thing being added to.
     */
    val generating: Boolean = false,
    val transientFailure: FailureReason? = null,
)

/**
 * Summaries (v2 plan §7, step 6 of §8).
 *
 * **This was blocked and is not any more.** `SummaryFacts` carries verb-keyed
 * maps whose legal keys were in no schema, and writing the parser against
 * guessed strings would have surfaced as a zero on screen — which reads exactly
 * like a true zero. Tracker 110 published the vocabulary as named enums, so the
 * keys are now known rather than assumed.
 */
@HiltViewModel
class SummariesViewModel @Inject constructor(
    private val repository: SummaryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SummariesUiState())
    val state: StateFlow<SummariesUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(content = LoadState.Loading) }
            val period = _state.value.period
            _state.update { current ->
                current.copy(
                    content = try {
                        LoadState.Ready(repository.summaries(period = period))
                    } catch (e: Exception) {
                        LoadState.Failed(e.toFailureReason())
                    },
                )
            }
        }
    }

    fun selectPeriod(period: SummaryPeriod?) {
        _state.update { it.copy(period = if (it.period == period) null else period) }
        load()
    }

    /**
     * Writes one for today, or for the current week.
     *
     * `replace = true`, and that is the right default **for a button somebody
     * pressed**: they are asking for this period to be written again, and
     * quietly keeping the old one would look like the button did nothing. The
     * repository's own default is the opposite, for callers that are not a
     * deliberate tap.
     */
    fun generate(period: SummaryPeriod) {
        if (_state.value.generating) return
        _state.update { it.copy(generating = true) }
        viewModelScope.launch {
            try {
                repository.generate(period = period, replace = true)
                _state.update { it.copy(generating = false) }
                load()
            } catch (e: Exception) {
                _state.update {
                    it.copy(generating = false, transientFailure = e.toFailureReason())
                }
            }
        }
    }

    fun dismissFailure() = _state.update { it.copy(transientFailure = null) }
}
