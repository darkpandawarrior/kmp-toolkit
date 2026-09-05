package com.siddharth.kmp.llmchat

import com.siddharth.kmp.result.AiFailure
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Same Dispatchers.Unconfined rationale as LlmChatSmokeTest.mockEngine: keeps the mock response on
// the calling thread so it resolves synchronously under runTest's virtual clock.
private fun sseMockEngine(vararg sseLines: String, status: HttpStatusCode = HttpStatusCode.OK) =
    MockEngine(
        MockEngineConfig().apply {
            dispatcher = Dispatchers.Unconfined
            addHandler {
                respond(
                    content = sseLines.joinToString("\n"),
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                )
            }
        },
    )

class StreamingProviderTest {
    @Test
    fun anthropicProvider_completeStream_emitsTokensInOrder() =
        runTest {
            val engine =
                sseMockEngine(
                    """data: {"type":"message_start"}""",
                    """data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Hel"}}""",
                    """data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"lo"}}""",
                    """data: {"type":"message_stop"}""",
                )
            val provider = AnthropicProvider(apiKey = "test-key", engine = engine)

            val chunks = provider.completeStream(listOf(AiMessage(AiMessage.Role.USER, "hi"))).toList()

            assertEquals(listOf(AiChunk.Token("Hel"), AiChunk.Token("lo")), chunks)
        }

    @Test
    fun anthropicProvider_completeStream_emptyStream_emitsFailedEmptyReply() =
        runTest {
            val engine = sseMockEngine("""data: {"type":"message_start"}""")
            val provider = AnthropicProvider(apiKey = "test-key", engine = engine)

            val chunks = provider.completeStream(listOf(AiMessage(AiMessage.Role.USER, "hi"))).toList()

            assertEquals(listOf(AiChunk.Failed(AiFailure.EmptyReply)), chunks)
        }

    @Test
    fun anthropicProvider_completeStream_noKey_emitsFailedNoKey_withoutCallingNetwork() =
        runTest {
            val chunks = AnthropicProvider(apiKey = "").completeStream(listOf(AiMessage(AiMessage.Role.USER, "hi"))).toList()

            assertEquals(listOf(AiChunk.Failed(AiFailure.NoKey)), chunks)
        }

    @Test
    fun anthropicProvider_completeStream_mapsUnauthorized() =
        runTest {
            val engine = sseMockEngine("", status = HttpStatusCode.Unauthorized)
            val provider = AnthropicProvider(apiKey = "bad-key", engine = engine)

            val chunks = provider.completeStream(listOf(AiMessage(AiMessage.Role.USER, "hi"))).toList()

            assertEquals(listOf(AiChunk.Failed(AiFailure.Unauthorized)), chunks)
        }

    @Test
    fun openAiProvider_completeStream_emitsTokensInOrder_andStopsAtDone() =
        runTest {
            val engine =
                sseMockEngine(
                    """data: {"choices":[{"delta":{"content":"Hel"}}]}""",
                    """data: {"choices":[{"delta":{"content":"lo"}}]}""",
                    "data: [DONE]",
                )
            val provider = OpenAiProvider(apiKey = "test-key", engine = engine)

            val chunks = provider.completeStream(listOf(AiMessage(AiMessage.Role.USER, "hi"))).toList()

            assertEquals(listOf(AiChunk.Token("Hel"), AiChunk.Token("lo")), chunks)
        }

    @Test
    fun openAiProvider_completeStream_mapsRateLimited() =
        runTest {
            val engine = sseMockEngine("", status = HttpStatusCode.TooManyRequests)
            val provider = OpenAiProvider(apiKey = "test-key", engine = engine)

            val chunks = provider.completeStream(listOf(AiMessage(AiMessage.Role.USER, "hi"))).toList()

            assertEquals(listOf(AiChunk.Failed(AiFailure.RateLimited)), chunks)
        }

    @Test
    fun geminiProvider_completeStream_emitsTokensInOrder() =
        runTest {
            val engine =
                sseMockEngine(
                    """data: {"candidates":[{"content":{"parts":[{"text":"Hel"}]}}]}""",
                    """data: {"candidates":[{"content":{"parts":[{"text":"lo"}]}}]}""",
                )
            val provider = GeminiProvider(apiKey = "test-key", engine = engine)

            val chunks = provider.completeStream(listOf(AiMessage(AiMessage.Role.USER, "hi"))).toList()

            assertEquals(listOf(AiChunk.Token("Hel"), AiChunk.Token("lo")), chunks)
        }

    @Test
    fun geminiProvider_completeStream_noKey_emitsFailedNoKey() =
        runTest {
            val chunks = GeminiProvider(apiKey = "").completeStream(listOf(AiMessage(AiMessage.Role.USER, "hi"))).toList()

            assertEquals(listOf(AiChunk.Failed(AiFailure.NoKey)), chunks)
        }

    @Test
    fun defaultCompleteStream_replaysCompleteResultAsSingleChunk() =
        runTest {
            // AiProvider.completeStream's default implementation, exercised through a minimal
            // implementer that doesn't override it — the fallback every non-HTTP AiProvider gets.
            class SingleShotProvider(private val text: String) : AiProvider {
                override val id = "single-shot"
                override val displayName = id

                override suspend fun isAvailable() = true

                override suspend fun complete(
                    messages: List<AiMessage>,
                    config: AiConfig,
                ) = com.siddharth.kmp.result.Result.Success<String>(text)
            }

            val chunks = SingleShotProvider("hello").completeStream(emptyList()).toList()

            assertEquals(listOf(AiChunk.Token("hello")), chunks)
        }
}
