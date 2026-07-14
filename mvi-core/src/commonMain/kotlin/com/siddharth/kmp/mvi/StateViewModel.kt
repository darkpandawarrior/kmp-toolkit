package com.siddharth.kmp.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State-plumbing-only base for screens that don't need one-shot navigation effects (yet).
 *
 * No Action/Effect generics, no [kotlinx.coroutines.channels.Channel], no broadcast — just a
 * [StateFlow] of `S` and a [setState] reducer. Reach for [BaseViewModel] instead the moment a
 * screen needs a one-shot effect (navigation, toast, one-shot dialog); this class won't grow that
 * capability in place, it's deliberately the smaller of the two.
 */
abstract class StateViewModel<S : Any>(initial: S) : ViewModel() {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()

    protected val currentState: S get() = _state.value

    /**
     * Atomically transform state via [MutableStateFlow.update] (CAS loop — no lost updates under
     * contention). CONTRACT: [reducer] MUST be pure; `update` re-runs it on CAS retry, so a reducer
     * with side effects would double-execute.
     */
    protected fun setState(reducer: S.() -> S) {
        _state.update { it.reducer() }
    }
}
