package com.siddharth.kmp.ai

import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises [FoundationModelsOnDeviceLlm]'s own mapping logic (native `String?`/callback shape ->
 * [OnDeviceLlm]'s typed `AiResult`/`Flow` contract) against a fake [NativeLlm] injected through the
 * constructor — no real bridge, no Xcode, no device needed. What this can't cover: the real Swift
 * bridge (`ai/ios-bridge/FoundationModelsBridge.swift`) actually calling `LanguageModelSession`, or
 * cancelling mid-generation on a real async native Task — those need a consuming app + device/
 * simulator with Apple Intelligence, outside what this repo can run.
 */
class FoundationModelsOnDeviceLlmTest {
    private class FakeNativeLlm(
        private val available: Boolean = true,
        private val reply: String? = "a reply",
        private val chunks: List<String> = emptyList(),
    ) : NativeLlm {
        var cancelled = false
            private set

        override fun isAvailable(): Boolean = available

        override suspend fun generate(prompt: String): String? = reply

        override fun generateStream(
            prompt: String,
            callback: NativeLlmStreamCallback,
        ): NativeLlmCancelHandle {
            chunks.forEach { callback.onPartial(it) }
            callback.onComplete()
            return NativeLlmCancelHandle { cancelled = true }
        }
    }

    private fun llmWith(fake: NativeLlm): FoundationModelsOnDeviceLlm {
        val bridge = InjectableNativeLlm().apply { generator = fake }
        return FoundationModelsOnDeviceLlm(bridge)
    }

    @Test
    fun isAvailable_reflectsTheInjectedBridge() {
        assertTrue(llmWith(FakeNativeLlm(available = true)).isAvailable())
        assertFalse(llmWith(FakeNativeLlm(available = false)).isAvailable())
    }

    @Test
    fun generate_declinesWithNotSupportedOnPlatform_whenNoBridgeIsAvailable() =
        runTest {
            val result = FoundationModelsOnDeviceLlm(InjectableNativeLlm()).generate("hi")
            assertEquals(Result.Failure(AiFailure.NotSupportedOnPlatform), result)
        }

    @Test
    fun generate_succeeds_whenTheBridgeAnswers() =
        runTest {
            val result = llmWith(FakeNativeLlm(reply = "hello")).generate("hi")
            assertEquals(Result.Success("hello"), result)
        }

    @Test
    fun generate_declinesWithEmptyReply_whenAnAvailableBridgeAnswersNull() =
        runTest {
            val result = llmWith(FakeNativeLlm(available = true, reply = null)).generate("hi")
            assertEquals(Result.Failure(AiFailure.EmptyReply), result)
        }

    @Test
    fun capabilities_reportsStreamingTrue_andWhyItsOffWhenNoBridgeIsRegistered() =
        runTest {
            val caps = FoundationModelsOnDeviceLlm(InjectableNativeLlm()).capabilities()
            assertTrue(caps.streaming)
            assertEquals(AiFailure.NotSupportedOnPlatform, caps.unavailableReason)
        }

    @Test
    fun generateStream_emitsTheBridgesChunksInOrder() =
        runTest {
            val fake = FakeNativeLlm(chunks = listOf("Hel", "lo"))
            assertEquals(listOf("Hel", "lo"), llmWith(fake).generateStream("hi").toList())
        }

    @Test
    fun generateStream_completesEmpty_whenNoBridgeIsAvailable() =
        runTest {
            assertTrue(FoundationModelsOnDeviceLlm(InjectableNativeLlm()).generateStream("hi").toList().isEmpty())
        }

    @Test
    fun generateStream_cancelsTheNativeHandle_onceCollectionEnds() =
        runTest {
            val fake = FakeNativeLlm(chunks = listOf("only"))
            llmWith(fake).generateStream("hi").toList()
            assertTrue(fake.cancelled, "awaitClose must forward cancellation to the native handle")
        }

    @Test
    fun generateStream_parts_worksForASingleTextPart() =
        runTest {
            val fake = FakeNativeLlm(chunks = listOf("ok"))
            val result = llmWith(fake).generateStream(listOf(LlmPart.Text("hi"))).toList()
            assertEquals(listOf("ok"), result)
        }

    @Test
    fun generateStream_parts_declinesAnythingButASingleTextPart() =
        runTest {
            val fake = FakeNativeLlm(chunks = listOf("unused"))
            val llm = llmWith(fake)

            assertTrue(llm.generateStream(listOf(LlmPart.Image(byteArrayOf(1)))).toList().isEmpty())
            assertTrue(llm.generateStream(listOf(LlmPart.Text("a"), LlmPart.Text("b"))).toList().isEmpty())
        }
}
