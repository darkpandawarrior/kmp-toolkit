package com.siddharth.kmp.ai

import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
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
        private val result: AiResult<String>,
    ) : OnDeviceLlm {
        override fun isAvailable() = true

        override suspend fun generate(prompt: String): AiResult<String> = result
    }

    @Test
    fun generateStream_prompt_replays_generate_result_as_single_emission() =
        runTest {
            val backend = FakeBackend(Result.Success("hello"))
            assertEquals(listOf("hello"), backend.generateStream("prompt").toList())
        }

    @Test
    fun generateStream_parts_replays_generate_result_as_single_emission() =
        runTest {
            val backend = FakeBackend(Result.Success("hello"))
            assertEquals(listOf("hello"), backend.generateStream(listOf(LlmPart.Text("prompt"))).toList())
        }

    @Test
    fun generateStream_emits_nothing_when_generate_fails() =
        runTest {
            val backend = FakeBackend(Result.Failure(AiFailure.ModelNotResident))
            assertTrue(backend.generateStream("prompt").toList().isEmpty())
        }

    @Test
    fun generate_parts_defaultOverload_rejectsNonTextParts_asNotSupportedOnPlatform() =
        runTest {
            val backend = FakeBackend(Result.Success("unused"))
            assertEquals(
                Result.Failure(AiFailure.NotSupportedOnPlatform),
                backend.generate(listOf(LlmPart.Image(byteArrayOf(1)))),
            )
        }
}
