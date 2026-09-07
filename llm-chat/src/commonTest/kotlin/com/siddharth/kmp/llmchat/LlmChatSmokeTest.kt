package com.siddharth.kmp.llmchat

import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.Result
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class FakeProvider(
    override val id: String,
    private val available: Boolean,
) : AiProvider {
    override val displayName = id

    override suspend fun isAvailable() = available

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): Result<String, AiFailure> = Result.Success(id)
}

// MockEngine defaults to dispatching its response on Dispatchers.IO — a real dispatcher invisible
// to runTest's virtual clock. That races complete()'s internal withTimeout(5_000): the scheduler
// sees no pending work on the test dispatcher and advances straight to the timeout before the mock
// response lands. Dispatchers.Unconfined keeps the mock's coroutine on the calling thread instead,
// so it resolves before control ever returns to the scheduler.
private fun mockEngine(status: HttpStatusCode, body: String) =
    MockEngine(
        MockEngineConfig().apply {
            dispatcher = Dispatchers.Unconfined
            addHandler {
                respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        },
    )

/** Records the single request it receives (url + headers + body) alongside the mocked response. */
private class CapturedRequest(val url: String, val headers: Map<String, String>, val body: String)

private fun capturingMockEngine(status: HttpStatusCode, body: String): Pair<MockEngine, () -> CapturedRequest?> {
    var captured: CapturedRequest? = null
    val engine =
        MockEngine(
            MockEngineConfig().apply {
                dispatcher = Dispatchers.Unconfined
                addHandler { request ->
                    captured =
                        CapturedRequest(
                            url = request.url.toString(),
                            headers = request.headers.entries().associate { it.key to it.value.joinToString(",") },
                            body = (request.body as? TextContent)?.text.orEmpty(),
                        )
                    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            },
        )
    return engine to { captured }
}

class LlmChatSmokeTest {
    @Test
    fun buildProviderChain_ordersOnDeviceThenCloudThenFallback_skippingBlankKeys() {
        val onDevice = FakeProvider("on_device", available = true)
        val fallback = FakeProvider("fallback", available = true)
        val config =
            AiProviderConfig(
                useOnDevice = true,
                anthropicKey = "sk-ant",
                openAiKey = " ", // blank — must be skipped
                geminiKey = "gk",
            )

        val chain = buildProviderChain(config, fallback = fallback, onDevice = onDevice)

        assertEquals(listOf("on_device", "anthropic", "gemini", "fallback"), chain.map { it.id })
    }

    @Test
    fun buildProviderChain_omitsOnDevice_whenUseOnDeviceFalse() {
        val chain = buildProviderChain(AiProviderConfig(useOnDevice = true), fallback = FakeProvider("fallback", true))
        // onDevice param not supplied — chain must not crash and must not include an on-device slot.
        assertTrue(chain.none { it.id == "on_device" })
    }

    @Test
    fun buildProviderChain_selectedProviderComesFirst_evenWithAnEarlierKeyAlsoSet() {
        // anthropicKey is set (and would normally sort first) but OPENAI is selected — selecting a
        // provider must actually pick it, not just get tried after whichever key happens to be set.
        val config =
            AiProviderConfig(
                selectedProvider = ProviderId.OPENAI,
                anthropicKey = "sk-ant",
                openAiKey = "sk-openai",
                geminiKey = "gk",
            )

        val chain = buildProviderChain(config, fallback = FakeProvider("fallback", true))

        assertEquals(listOf("openai", "anthropic", "gemini", "fallback"), chain.map { it.id })
    }

    @Test
    fun buildProviderChain_defaultSelection_leavesFixedPriorityOrderUnchanged() {
        // selectedProvider defaults to OFFLINE_FALLBACK — no cloud provider is "selected", so the
        // existing Anthropic > OpenAI > Gemini priority order is unaffected.
        val config = AiProviderConfig(anthropicKey = "sk-ant", openAiKey = "sk-openai", geminiKey = "gk")

        val chain = buildProviderChain(config, fallback = FakeProvider("fallback", true))

        assertEquals(listOf("anthropic", "openai", "gemini", "fallback"), chain.map { it.id })
    }

    @Test
    fun firstAvailable_returnsFirstAvailableProvider_elseFallback() =
        runTest {
            val unavailable = FakeProvider("a", available = false)
            val available = FakeProvider("b", available = true)
            val fallback = FakeProvider("fallback", available = true)

            assertSame(available, firstAvailable(listOf(unavailable, available), fallback))
            assertSame(fallback, firstAvailable(listOf(unavailable), fallback))
        }

    @Test
    fun cloudProviders_areUnavailable_withBlankApiKey() =
        runTest {
            assertTrue(!AnthropicProvider("").isAvailable())
            assertTrue(!OpenAiProvider("").isAvailable())
            assertTrue(!GeminiProvider("").isAvailable())
        }

    @Test
    fun cloudProviders_complete_returnsNoKey_withBlankApiKey() =
        runTest {
            assertEquals(Result.Failure(AiFailure.NoKey), AnthropicProvider("").complete(emptyList()))
            assertEquals(Result.Failure(AiFailure.NoKey), OpenAiProvider("").complete(emptyList()))
            assertEquals(Result.Failure(AiFailure.NoKey), GeminiProvider("").complete(emptyList()))
        }

    @Test
    fun anthropicProvider_complete_parsesMockedResponse() =
        runTest {
            val provider =
                AnthropicProvider(
                    apiKey = "test-key",
                    engine = mockEngine(HttpStatusCode.OK, """{"content":[{"text":"hello"}]}"""),
                )

            val result = provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi")))

            assertEquals(Result.Success("hello"), result)
        }

    @Test
    fun anthropicProvider_complete_mapsUnauthorized() =
        runTest {
            val provider =
                AnthropicProvider(apiKey = "bad-key", engine = mockEngine(HttpStatusCode.Unauthorized, """{}"""))

            assertEquals(Result.Failure(AiFailure.Unauthorized), provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi"))))
        }

    @Test
    fun anthropicProvider_complete_mapsRateLimited() =
        runTest {
            val provider =
                AnthropicProvider(apiKey = "test-key", engine = mockEngine(HttpStatusCode.TooManyRequests, """{}"""))

            assertEquals(Result.Failure(AiFailure.RateLimited), provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi"))))
        }

    @Test
    fun anthropicProvider_complete_mapsEmptyReply() =
        runTest {
            val provider =
                AnthropicProvider(apiKey = "test-key", engine = mockEngine(HttpStatusCode.OK, """{"content":[]}"""))

            assertEquals(Result.Failure(AiFailure.EmptyReply), provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi"))))
        }

    @Test
    fun anthropicProvider_complete_mapsServerErrorToNetwork() =
        runTest {
            val provider =
                AnthropicProvider(
                    apiKey = "test-key",
                    engine = mockEngine(HttpStatusCode.InternalServerError, """{"error":"boom"}"""),
                )

            assertEquals(Result.Failure(AiFailure.Network), provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi"))))
        }

    @Test
    fun anthropicProvider_complete_sendsConfiguredTemperature() =
        runTest {
            val (engine, lastRequest) = capturingMockEngine(HttpStatusCode.OK, """{"content":[{"text":"hi"}]}""")
            val provider = AnthropicProvider(apiKey = "test-key", engine = engine)

            provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi")), AiConfig(temperature = 0.2f))

            assertTrue(lastRequest()!!.body.contains(""""temperature":0.2"""))
        }

    @Test
    fun openAiProvider_complete_parsesMockedResponse() =
        runTest {
            val provider =
                OpenAiProvider(
                    apiKey = "test-key",
                    engine =
                        mockEngine(
                            HttpStatusCode.OK,
                            """{"choices":[{"message":{"role":"assistant","content":"hello"}}]}""",
                        ),
                )

            val result = provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi")))

            assertEquals(Result.Success("hello"), result)
        }

    @Test
    fun openAiProvider_complete_mapsUnauthorized() =
        runTest {
            val provider =
                OpenAiProvider(apiKey = "bad-key", engine = mockEngine(HttpStatusCode.Unauthorized, """{}"""))

            assertEquals(Result.Failure(AiFailure.Unauthorized), provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi"))))
        }

    @Test
    fun openAiProvider_complete_mapsMalformedJsonToNetwork() =
        runTest {
            val provider = OpenAiProvider(apiKey = "test-key", engine = mockEngine(HttpStatusCode.OK, "not json"))

            assertEquals(Result.Failure(AiFailure.Network), provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi"))))
        }

    @Test
    fun geminiProvider_complete_parsesMockedResponse() =
        runTest {
            val provider =
                GeminiProvider(
                    apiKey = "test-key",
                    engine =
                        mockEngine(
                            HttpStatusCode.OK,
                            """{"candidates":[{"content":{"parts":[{"text":"hello"}]}}]}""",
                        ),
                )

            val result = provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi")))

            assertEquals(Result.Success("hello"), result)
        }

    @Test
    fun geminiProvider_complete_mapsRateLimited() =
        runTest {
            val provider =
                GeminiProvider(apiKey = "test-key", engine = mockEngine(HttpStatusCode.TooManyRequests, """{}"""))

            assertEquals(Result.Failure(AiFailure.RateLimited), provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi"))))
        }

    @Test
    fun geminiProvider_complete_mapsMalformedJsonToNetwork() =
        runTest {
            val provider = GeminiProvider(apiKey = "test-key", engine = mockEngine(HttpStatusCode.OK, "not json"))

            assertEquals(Result.Failure(AiFailure.Network), provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi"))))
        }

    @Test
    fun geminiProvider_complete_sendsApiKeyAsHeader_notUrlQueryParam() =
        runTest {
            val (engine, lastRequest) =
                capturingMockEngine(HttpStatusCode.OK, """{"candidates":[{"content":{"parts":[{"text":"hi"}]}}]}""")
            val provider = GeminiProvider(apiKey = "sekrit-key", engine = engine)

            provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi")))

            val request = lastRequest()!!
            assertEquals("sekrit-key", request.headers["x-goog-api-key"])
            assertFalse(request.url.contains("sekrit-key"), "API key leaked into the URL: ${request.url}")
        }

    @Test
    fun completeOrBlank_collapsesFailureToEmptyString() =
        runTest {
            @Suppress("DEPRECATION")
            val text = AnthropicProvider("").completeOrBlank(listOf(AiMessage(AiMessage.Role.USER, "hi")))
            assertEquals("", text)
        }
}
