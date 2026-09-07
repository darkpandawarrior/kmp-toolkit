package com.siddharth.kmp.ai

import com.siddharth.kmp.llmchat.AiChunk
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.result.AiCapabilities
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cloud as the on-device fallback tier: [CloudOnDeviceLlm] must behave like every other
 * [OnDeviceLlm] backend so it slots straight into [CompositeOnDeviceLlm] — skip unavailable
 * providers, use the first that actually produces output, and surface the last provider's failure
 * reason (or [AiFailure.NoKey] for an empty chain) when none does.
 */
class CloudOnDeviceLlmTest {
    private class FakeProvider(
        override val id: String,
        private val available: Boolean,
        private val result: AiResult<String> = Result.Success(id),
        private val streamed: List<AiChunk> = listOf(AiChunk.Token(id)),
        private val ownCapabilities: AiCapabilities? = null,
    ) : AiProvider {
        override val displayName: String = id
        var calls = 0
        var lastMessages: List<AiMessage>? = null
            private set

        override suspend fun isAvailable(): Boolean = available

        override suspend fun complete(
            messages: List<AiMessage>,
            config: AiConfig,
        ): AiResult<String> {
            calls++
            lastMessages = messages
            return result
        }

        override fun completeStream(
            messages: List<AiMessage>,
            config: AiConfig,
        ): Flow<AiChunk> {
            lastMessages = messages
            return flowOf(*streamed.toTypedArray())
        }

        override suspend fun capabilities(): AiCapabilities = ownCapabilities ?: super.capabilities()
    }

    @Test
    fun isAvailable_isTrueOnlyWhenProvidersIsNonEmpty() {
        assertFalse(CloudOnDeviceLlm(emptyList()).isAvailable())
        assertTrue(CloudOnDeviceLlm(listOf(FakeProvider("anthropic", available = false))).isAvailable())
    }

    @Test
    fun generate_skipsUnavailableAndUsesFirstAvailableProvider() =
        runTest {
            val anthropic = FakeProvider("anthropic", available = false)
            val openAi = FakeProvider("openai", available = true, result = Result.Success("hi from openai"))
            val llm = CloudOnDeviceLlm(listOf(anthropic, openAi))

            assertEquals(Result.Success("hi from openai"), llm.generate("prompt"))
            assertEquals(0, anthropic.calls, "unavailable provider must not be invoked")
            assertEquals(1, openAi.calls)
            assertEquals("prompt", openAi.lastMessages!!.single().content)
            assertEquals(AiMessage.Role.USER, openAi.lastMessages!!.single().role)
        }

    @Test
    fun generate_fallsThroughWhenAnAvailableProviderFails() =
        runTest {
            val first = FakeProvider("anthropic", available = true, result = Result.Failure(AiFailure.RateLimited))
            val second = FakeProvider("openai", available = true, result = Result.Success("ok"))
            val llm = CloudOnDeviceLlm(listOf(first, second))

            assertEquals(Result.Success("ok"), llm.generate("prompt"))
            assertEquals(1, first.calls)
            assertEquals(1, second.calls)
        }

    @Test
    fun generate_surfacesLastProvidersFailureReason_whenEveryProviderFails() =
        runTest {
            val llm =
                CloudOnDeviceLlm(
                    listOf(
                        FakeProvider("anthropic", available = true, result = Result.Failure(AiFailure.Unauthorized)),
                        FakeProvider("openai", available = true, result = Result.Failure(AiFailure.Timeout)),
                    ),
                )
            assertEquals(Result.Failure(AiFailure.Timeout), llm.generate("prompt"))
        }

    @Test
    fun generate_returnsNoKey_forAnEmptyProviderChain() =
        runTest {
            assertEquals(Result.Failure(AiFailure.NoKey), CloudOnDeviceLlm(emptyList()).generate("prompt"))
        }

    @Test
    fun generateStream_streamsTokensFromTheFirstAvailableProvider() =
        runTest {
            val anthropic = FakeProvider("anthropic", available = false)
            val openAi =
                FakeProvider(
                    "openai",
                    available = true,
                    streamed = listOf(AiChunk.Token("hel"), AiChunk.Token("lo")),
                )
            val llm = CloudOnDeviceLlm(listOf(anthropic, openAi))

            assertEquals(listOf("hel", "lo"), llm.generateStream("prompt").toList())
        }

    @Test
    fun generateStream_emitsNothing_whenNoProviderIsAvailable() =
        runTest {
            val llm = CloudOnDeviceLlm(listOf(FakeProvider("anthropic", available = false)))
            assertEquals(emptyList(), llm.generateStream("prompt").toList())
        }

    @Test
    fun capabilities_delegatesToTheFirstAvailableProvidersOwnAnswer() =
        runTest {
            val caps = AiCapabilities(streaming = true, multimodal = false, honoredConfigFields = setOf("temperature"), unavailableReason = null)
            val anthropic = FakeProvider("anthropic", available = false)
            val openAi = FakeProvider("openai", available = true, ownCapabilities = caps)
            val llm = CloudOnDeviceLlm(listOf(anthropic, openAi))

            assertEquals(caps, llm.capabilities())
        }

    @Test
    fun capabilities_reportsNoKey_whenNoProviderIsAvailable() =
        runTest {
            val llm = CloudOnDeviceLlm(emptyList())
            assertEquals(AiFailure.NoKey, llm.capabilities().unavailableReason)
        }
}
