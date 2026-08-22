package com.ar13x.jarvis.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.core.update.SemVer
import com.ar13x.jarvis.core.update.UpdateRepository
import com.ar13x.jarvis.core.update.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The update banner's state. Thin by design — the repository is a singleton, so
 * every screen that asks sees the same answer without any of them coordinating.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
) : ViewModel() {

    val status: StateFlow<UpdateStatus> = repository.status

    /** Cheap and guarded internally, so calling it on every resume is fine. */
    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun dismiss(version: SemVer) {
        viewModelScope.launch { repository.dismiss(version) }
    }
}
