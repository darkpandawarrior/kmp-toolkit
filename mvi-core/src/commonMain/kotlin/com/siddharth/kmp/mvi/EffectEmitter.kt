package com.siddharth.kmp.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * One-shot effect delivery with no dependency on `androidx.lifecycle.ViewModel`.
 *
 * Wraps a buffered [Channel] the same way [BaseViewModel.emitEffect] does, but takes only a
 * [CoroutineScope] — for consumers whose ViewModel-equivalent isn't built on
 * `androidx.lifecycle.ViewModel` (e.g. a hand-rolled presenter). Pure coroutines, no platform
 * dependency, so it compiles on every target this library declares.
 */
class EffectEmitter<E : Any>(private val scope: CoroutineScope) {
    private val channel = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = channel.receiveAsFlow()

    /** Deliver once to the active collector. */
    fun emit(effect: E) {
        scope.launch { channel.send(effect) }
    }
}
