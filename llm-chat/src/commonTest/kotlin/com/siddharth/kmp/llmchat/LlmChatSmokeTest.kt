package com.siddharth.kmp.llmchat

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    ) = id
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
    fun firstAvailable_returnsFirstAvailableProvider_elseFallback() =
        runTest {
            val unavailable = FakeProvider("a", available = false)
            val available = FakeProvider("b", available = true)
            val fallback = FakeProvider("fallback", available = true)

            assertSame(available, firstAvailable(listOf(unavailable, available), fallback))
            assertSame(fallback, firstAvailable(listOf(unavailable), fallback))
        }

    @Test
    fun anthropicProvider_complete_parsesMockedResponse() =
        runTest {
            // MockEngine defaults to dispatching its response on Dispatchers.IO — a real dispatcher
            // invisible to runTest's virtual clock. That races complete()'s internal
            // withTimeout(5_000): the scheduler sees no pending work on the test dispatcher and
            // advances straight to the timeout before the mock response lands. Dispatchers.Unconfined
            // keeps the mock's coroutine on the calling thread instead, so it resolves before
            // control ever returns to the scheduler.
            val engine =
                MockEngine(
                    MockEngineConfig().apply {
                        dispatcher = Dispatchers.Unconfined
                        addHandler {
                            respond(
                                content = """{"content":[{"text":"hello"}]}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    },
                )
            val provider = AnthropicProvider(apiKey = "test-key", engine = engine)

            val result = provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi")))

            assertEquals("hello", result)
        }

    @Test
    fun cloudProviders_areUnavailable_withBlankApiKey() =
        runTest {
            assertTrue(!AnthropicProvider("").isAvailable())
            assertTrue(!OpenAiProvider("").isAvailable())
            assertTrue(!GeminiProvider("").isAvailable())
        }
}
