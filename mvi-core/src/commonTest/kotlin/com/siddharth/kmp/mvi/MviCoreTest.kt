package com.siddharth.kmp.mvi

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MviCoreTest {

    @Test
    fun effectEmitter_emit_deliversValueThroughEffects() = runTest {
        val emitter = EffectEmitter<String>(this)
        emitter.emit("navigate")
        assertEquals("navigate", emitter.effects.first())
    }

    private class TestStateViewModel(initial: Int) : StateViewModel<Int>(initial) {
        fun increment() = setState { this + 1 }
    }

    @Test
    fun stateViewModel_setState_updatesState() {
        val viewModel = TestStateViewModel(initial = 0)
        viewModel.increment()
        assertEquals(1, viewModel.state.value)
    }

    /**
     * Documents the setState contract in the test name: the reducer MUST be pure. Since setState is
     * backed by `MutableStateFlow.update`, which re-invokes the lambda on CAS retry, any side effect
     * inside the reducer would double-execute under contention. A pure reducer is idempotent per
     * retry and yields exactly the mapped value.
     */
    @Test
    fun stateViewModel_setState_reducerMustBePure_pureReducerMapsExactlyOnce() {
        val viewModel = TestStateViewModel(initial = 41)
        viewModel.increment()
        assertEquals(42, viewModel.state.value)
    }

    @Test
    fun effectEmitter_deliversEffectsInEmissionOrder() = runTest {
        val emitter = EffectEmitter<Int>(this)
        emitter.emit(1)
        emitter.emit(2)
        emitter.emit(3)
        assertEquals(listOf(1, 2, 3), emitter.effects.take(3).toList())
    }
}
