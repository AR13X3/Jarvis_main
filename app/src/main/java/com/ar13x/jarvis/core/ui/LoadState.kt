package com.ar13x.jarvis.core.ui

import com.ar13x.jarvis.core.model.FailureReason

/**
 * Loading and failure are *states*, not booleans scattered across a UI state
 * class (plan §8.1). Modelling them as a sum type is what stops the "loading
 * true and error non-null at the same time" class of bug from being expressible.
 */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
    data class Failed(val reason: FailureReason) : LoadState<Nothing>

    val dataOrNull: T? get() = (this as? Ready)?.data
}

inline fun <T, R> LoadState<T>.map(transform: (T) -> R): LoadState<R> = when (this) {
    is LoadState.Loading -> LoadState.Loading
    is LoadState.Failed -> this
    is LoadState.Ready -> LoadState.Ready(transform(data))
}
