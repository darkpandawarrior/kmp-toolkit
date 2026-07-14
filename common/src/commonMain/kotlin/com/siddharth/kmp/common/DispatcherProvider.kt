package com.siddharth.kmp.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Injectable dispatchers so ViewModels/repositories are testable (swap for a test dispatcher).
 *
 * ponytail: `io` maps to [Dispatchers.Default] in common — `Dispatchers.IO` is JVM/Android-only and
 * absent from wasm/native commonMain. The Android data layer can override `io` with the real IO
 * dispatcher via an androidMain-provided instance if blocking IO ever needs it.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

object StandardDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val io: CoroutineDispatcher get() = Dispatchers.Default
}
