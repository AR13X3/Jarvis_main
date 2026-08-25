package com.ar13x.jarvis.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ar13x.jarvis.core.update.ReleaseNote
import com.ar13x.jarvis.core.update.SemVer
import com.ar13x.jarvis.core.update.UpdateRepository
import com.ar13x.jarvis.core.update.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _checking = MutableStateFlow(false)
    /**
     * An explicit check needs visible feedback even when the answer is "no
     * change" — otherwise the button looks broken precisely when it is working.
     */
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _whatsNew = MutableStateFlow<List<ReleaseNote>>(emptyList())
    /** Populated when About asks. Empty means not loaded, or not reachable. */
    val whatsNew: StateFlow<List<ReleaseNote>> = _whatsNew.asStateFlow()

    /**
     * Loaded on demand rather than kept fresh — About is opened deliberately,
     * and a changelog nobody is looking at is not worth a background fetch.
     */
    fun loadWhatsNew() {
        if (_whatsNew.value.isNotEmpty()) return
        viewModelScope.launch { _whatsNew.value = repository.whatsNew() }
    }

    fun checkNow() {
        if (_checking.value) return
        _checking.value = true
        viewModelScope.launch {
            repository.refresh(force = true)
            // An explicit check should also refresh what it is offering to
            // show, or the notes below the button contradict the button.
            _whatsNew.value = repository.whatsNew()
            _checking.value = false
        }
    }

    /** Cheap and guarded internally, so calling it on every resume is fine. */
    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun dismiss(version: SemVer) {
        viewModelScope.launch { repository.dismiss(version) }
    }
}
