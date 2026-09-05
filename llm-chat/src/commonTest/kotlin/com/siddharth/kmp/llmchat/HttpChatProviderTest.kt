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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Same Dispatchers.Unconfined rationale as the other providers' tests: keeps the mock response on
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

private class HttpChatCapturedRequest(val url: String, val headers: Map<String, String>, val body: String)

private fun capturingSseMockEngine(vararg sseLines: String): Pair<MockEngine, () -> HttpChatCapturedRequest?> {
    var captured: HttpChatCapturedRequest? = null
    val engine =
        MockEngine(
            MockEngineConfig().apply {
                dispatcher = Dispatchers.Unconfined
                addHandler { request ->
                    captured =
                        HttpChatCapturedRequest(
                            url = request.url.toString(),
                            headers = request.headers.entries().associate { it.key to it.value.joinToString(",") },
                            body = (request.body as? TextContent)?.text.orEmpty(),
                        )
                    respond(
                        content = sseLines.joinToString("\n"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                    )
                }
            },
        )
    return engine to { captured }
}

class HttpChatProviderTest {
    @Test
    fun completeStream_emitsTokensInOrder_andIgnoresDoneSentinel() =
        runTest {
            val engine =
                sseMockEngine(
                    """data: {"text":"Hel"}""",
                    """data: {"text":"lo"}""",
                    "data: [DONE]",
                )
            val provider = HttpChatProvider(HttpChatConfig(endpoint = "https://example.test/chat"), engine = engine)

            val chunks = provider.completeStream(listOf(AiMessage(AiMessage.Role.USER, "hi"))).toList()

            assertEquals(listOf(AiChunk.Token("Hel"), AiChunk.Token("lo")), chunks)
        }

    @Test
    fun completeStream_emptyStream_emitsFailedEmptyReply() =
        runTest {
            val engine = sseMockEngine("data: [DONE]")
            val provider = HttpChatProvider(HttpChatConfig(endpoint = "https://example.test/chat"), engine = engine)

            val chunks = provider.completeStream(listOf(AiMessage(AiMessage.Role.USER, "hi"))).toList()

            assertEquals(listOf(AiChunk.Failed(AiFailure.EmptyReply)), chunks)
        }

    @Test
    fun completeStream_blankEndpoint_emitsFailedNoKey_withoutCallingNetwork() =
        runTest {
            val chunks =
                HttpChatProvider(HttpChatConfig(endpoint = ""))
                    .completeStream(listOf(AiMessage(AiMessage.Role.USER, "hi")))
                    .toList()

            assertEquals(listOf(AiChunk.Failed(AiFailure.NoKey)), chunks)
        }

    @Test
    fun completeStream_mapsUnauthorized() =
        runTest {
            val engine = sseMockEngine("", status = HttpStatusCode.Unauthorized)
            val provider = HttpChatProvider(HttpChatConfig(endpoint = "https://example.test/chat"), engine = engine)

            val chunks = provider.completeStream(listOf(AiMessage(AiMessage.Role.USER, "hi"))).toList()

            assertEquals(listOf(AiChunk.Failed(AiFailure.Unauthorized)), chunks)
        }

    @Test
    fun complete_joinsStreamedTokens_underTimeout() =
        runTest {
            val engine = sseMockEngine("""data: {"text":"Hel"}""", """data: {"text":"lo"}""")
            val provider = HttpChatProvider(HttpChatConfig(endpoint = "https://example.test/chat"), engine = engine)

            val result = provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi")))

            assertEquals(Result.Success("Hello"), result)
        }

    @Test
    fun complete_blankEndpoint_returnsNoKey_withoutCallingNetwork() =
        runTest {
            assertEquals(Result.Failure(AiFailure.NoKey), HttpChatProvider(HttpChatConfig(endpoint = "")).complete(emptyList()))
        }

    @Test
    fun complete_mapsRateLimited() =
        runTest {
            val engine = sseMockEngine("", status = HttpStatusCode.TooManyRequests)
            val provider = HttpChatProvider(HttpChatConfig(endpoint = "https://example.test/chat"), engine = engine)

            assertEquals(
                Result.Failure(AiFailure.RateLimited),
                provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi"))),
            )
        }

    @Test
    fun request_carriesModeAndSystemPrompt_separatelyFromMessages() =
        runTest {
            val (engine, lastRequest) = capturingSseMockEngine("""data: {"text":"hi"}""")
            val provider =
                HttpChatProvider(HttpChatConfig(endpoint = "https://example.test/chat", mode = "resume-coach"), engine = engine)

            provider.complete(
                listOf(
                    AiMessage(AiMessage.Role.SYSTEM, "You are a resume coach."),
                    AiMessage(AiMessage.Role.USER, "hi"),
                ),
            )

            val body = lastRequest()!!.body
            assertTrue(body.contains(""""mode":"resume-coach""""), body)
            assertTrue(body.contains(""""system":"You are a resume coach.""""), body)
            // the SYSTEM message must not also appear duplicated inside "messages".
            assertFalse(body.contains(""""role":"system""""), body)
        }

    @Test
    fun request_sendsOriginHeader_whenConfigured() =
        runTest {
            val (engine, lastRequest) = capturingSseMockEngine("""data: {"text":"hi"}""")
            val provider =
                HttpChatProvider(
                    HttpChatConfig(endpoint = "https://example.test/chat", originHeader = "https://portfolio.example"),
                    engine = engine,
                )

            provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi")))

            assertEquals("https://portfolio.example", lastRequest()!!.headers[HttpHeaders.Origin])
        }

    @Test
    fun request_omitsOriginHeader_whenNotConfigured() =
        runTest {
            val (engine, lastRequest) = capturingSseMockEngine("""data: {"text":"hi"}""")
            val provider = HttpChatProvider(HttpChatConfig(endpoint = "https://example.test/chat"), engine = engine)

            provider.complete(listOf(AiMessage(AiMessage.Role.USER, "hi")))

            assertNull(lastRequest()!!.headers[HttpHeaders.Origin])
        }

    @Test
    fun isAvailable_false_withBlankEndpoint() =
        runTest {
            assertFalse(HttpChatProvider(HttpChatConfig(endpoint = "")).isAvailable())
            assertTrue(HttpChatProvider(HttpChatConfig(endpoint = "https://example.test/chat")).isAvailable())
        }

    @Test
    fun capabilities_reportsStreamingAndHonoredFields() =
        runTest {
            val provider = HttpChatProvider(HttpChatConfig(endpoint = "https://example.test/chat"))

            val capabilities = provider.capabilities()

            assertTrue(capabilities.streaming)
            assertEquals(setOf("maxTokens", "temperature", "timeoutMs"), capabilities.honoredConfigFields)
            assertNull(capabilities.unavailableReason)
        }
}
