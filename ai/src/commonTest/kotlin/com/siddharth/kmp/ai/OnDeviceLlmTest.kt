package com.siddharth.kmp.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [OnDeviceLlm.generateStream] default: every backend that doesn't override it (every actual
 * except [MlKitGenAiOnDeviceLlm] on Android) must still support streaming callers by replaying
 * [OnDeviceLlm.generate]'s result as a single emission.
 */
class OnDeviceLlmTest {
    private class FakeBackend(
        private val output: String?,
    ) : OnDeviceLlm {
        override fun isAvailable() = true

        override suspend fun generate(prompt: String): String? = output
    }

    @Test
    fun generateStream_prompt_replays_generate_result_as_single_emission() =
        runTest {
            val backend = FakeBackend(output = "hello")
            assertEquals(listOf("hello"), backend.generateStream("prompt").toList())
        }

    @Test
    fun generateStream_parts_replays_generate_result_as_single_emission() =
        runTest {
            val backend = FakeBackend(output = "hello")
            assertEquals(listOf("hello"), backend.generateStream(listOf(LlmPart.Text("prompt"))).toList())
        }

    @Test
    fun generateStream_emits_nothing_when_generate_returns_null() =
        runTest {
            val backend = FakeBackend(output = null)
            assertTrue(backend.generateStream("prompt").toList().isEmpty())
        }
}
