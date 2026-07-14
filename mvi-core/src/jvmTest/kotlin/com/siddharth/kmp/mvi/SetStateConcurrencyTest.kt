package com.siddharth.kmp.mvi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression proof for the setState CAS fix (kmp-toolkit-family Phase 1).
 *
 * Real OS threads ([Dispatchers.Default]) hammer `setState` concurrently. The pre-fix
 * `_state.value = _state.value.reducer()` is a non-atomic read-modify-write: two threads read the
 * same base and the second write clobbers the first, so the final count comes out LESS than
 * expected. The `_state.update { }` fix CAS-loops and converges to the exact expected count.
 *
 * Deliberately in jvmTest, not commonTest: JS/Wasm are single-threaded and Native `runTest`
 * serializes, so only the JVM host set can actually race and expose the lost-update bug. Run these
 * against the old plain-assignment implementation and they fail; against the fix they pass.
 */
class SetStateConcurrencyTest {

    private class CounterStateVm : StateViewModel<Int>(0) {
        fun inc() = setState { this + 1 }
    }

    private data class Count(val n: Int)

    private class CounterBaseVm : BaseViewModel<Count, Unit, Unit>(Count(0)) {
        fun inc() = setState { copy(n = n + 1) }
        override fun onAction(action: Unit) = Unit
    }

    private val workers = 8
    private val perWorker = 25_000

    @Test
    fun stateViewModel_concurrentSetState_convergesWithoutLostUpdates() = runBlocking {
        val vm = CounterStateVm()
        (0 until workers).map {
            launch(Dispatchers.Default) { repeat(perWorker) { vm.inc() } }
        }.joinAll()
        assertEquals(workers * perWorker, vm.state.value)
    }

    @Test
    fun baseViewModel_concurrentSetState_convergesWithoutLostUpdates() = runBlocking {
        val vm = CounterBaseVm()
        (0 until workers).map {
            launch(Dispatchers.Default) { repeat(perWorker) { vm.inc() } }
        }.joinAll()
        assertEquals(workers * perWorker, vm.state.value.n)
    }
}
