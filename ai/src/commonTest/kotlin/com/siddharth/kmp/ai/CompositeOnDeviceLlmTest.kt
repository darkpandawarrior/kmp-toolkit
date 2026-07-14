package com.siddharth.kmp.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Detection-ordered on-device fallback (ai-engineering.md §7): ML Kit Gemini Nano → MediaPipe Gemma
 * → (null → the heuristic tier answers upstream). The composite must skip unavailable backends, use
 * the first one that actually produces output, and return null when every backend declines.
 */
class CompositeOnDeviceLlmTest {
    private class FakeBackend(
        val name: String,
        private val available: Boolean,
        private val output: String?,
    ) : OnDeviceLlm {
        var calls = 0

        override fun isAvailable() = available

        override suspend fun generate(prompt: String): String? {
            calls++
            return output
        }
    }

    @Test
    fun skips_unavailable_and_uses_first_available_producing_output() =
        runTest {
            val mlkit = FakeBackend("mlkit", available = false, output = "nano")
            val mediapipe = FakeBackend("mediapipe", available = true, output = "gemma")
            val composite = CompositeOnDeviceLlm(listOf(mlkit, mediapipe))

            assertTrue(composite.isAvailable())
            assertEquals("gemma", composite.generate("prompt"))
            assertEquals(0, mlkit.calls, "unavailable backend must not be invoked")
            assertEquals(1, mediapipe.calls)
        }

    @Test
    fun falls_through_when_an_available_backend_yields_null() =
        runTest {
            val first = FakeBackend("mlkit", available = true, output = null)
            val second = FakeBackend("mediapipe", available = true, output = "gemma")
            val composite = CompositeOnDeviceLlm(listOf(first, second))

            assertEquals("gemma", composite.generate("prompt"))
            assertEquals(1, first.calls)
            assertEquals(1, second.calls)
        }

    @Test
    fun returns_null_when_no_backend_is_available() =
        runTest {
            val composite =
                CompositeOnDeviceLlm(
                    listOf(
                        FakeBackend("mlkit", available = false, output = "x"),
                        FakeBackend("mediapipe", available = false, output = "y"),
                    ),
                )
            assertFalse(composite.isAvailable())
            assertNull(composite.generate("prompt"))
        }
}
