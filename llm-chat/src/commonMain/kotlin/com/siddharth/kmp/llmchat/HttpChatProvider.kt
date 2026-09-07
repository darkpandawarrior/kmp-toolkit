package com.siddharth.kmp.llmchat

import com.siddharth.kmp.network.httpClientEngine
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import io.ktor.client.*
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Config for [HttpChatProvider]: the caller's own SSE chat backend, not a named vendor.
 *
 * @param endpoint the backend URL to POST to. Blank means "not configured" — [HttpChatProvider]
 *   reports [AiFailure.NoKey] for that, same bucket the three vendor providers use for a missing
 *   key, since this provider has no separate "no endpoint" reason in the shared [AiFailure] enum.
 * @param mode an app-defined string forwarded to the backend as-is (e.g. which persona/prompt-pack
 *   to use). `llm-chat` doesn't know or validate the values — the backend does.
 * @param originHeader sent as this request's `Origin` header when non-null, for a backend that
 *   allow-lists origins as a lightweight check on a public, keyless chat endpoint. Native engines
 *   (OkHttp/Darwin/CIO) send whatever is set here; a real browser (wasmJs) refuses to let a script
 *   override `Origin` — it's a forbidden header per the Fetch spec — so this is a no-op there and
 *   the browser's own Origin goes out instead.
 */
data class HttpChatConfig(
    val endpoint: String,
    val mode: String? = null,
    val originHeader: String? = null,
)

/**
 * [AiProvider] over a caller-supplied HTTP endpoint speaking the same `data: <json>` / `data:
 * [DONE]` SSE contract as [AnthropicProvider]/[OpenAiProvider]/[GeminiProvider] — the shape
 * `cv-siddharth-kmp` and HireSignal were each hand-rolling their own parser for. Every reply frame
 * decodes as [HttpChatStreamEvent]; the backend is expected to emit `{"text":"..."}` per token and
 * close the stream (optionally preceded by a `data: [DONE]` line, which [parseSseFrames] already
 * discards) rather than any vendor-specific event shape.
 */
class HttpChatProvider(
    private val httpConfig: HttpChatConfig,
    engine: HttpClientEngine = httpClientEngine(),
) : AiProvider {
    override val id = "http-chat"
    override val displayName = "HTTP Chat"

    private val client by lazy {
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    override suspend fun isAvailable() = httpConfig.endpoint.isNotBlank()

    override suspend fun capabilities() = httpCloudCapabilities()

    /**
     * No separate HTTP call of its own — collects [completeStream] under [AiConfig.timeoutMs] and
     * joins its tokens. One call site for the wire contract instead of a second copy of the request/
     * response handling that [completeStream] already has right.
     *
     * Unlike [AnthropicProvider]/[OpenAiProvider]/[GeminiProvider]'s `complete()`, there's no
     * `catch (_: Exception)` below the timeout catch — [completeStream] never lets an ordinary
     * exception escape its `channelFlow` (it turns those into [AiChunk.Failed] instead), so a plain
     * [CancellationException] here has nothing broader to fall into and already propagates
     * uncaught; a `catch (e: CancellationException) { throw e }` would be a no-op rethrow.
     */
    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> =
        try {
            withTimeout(config.timeoutMs) {
                val chunks = completeStream(messages, config).toList()
                chunks.filterIsInstance<AiChunk.Failed>().firstOrNull()?.let {
                    return@withTimeout Result.Failure(it.reason)
                }
                Result.Success(chunks.filterIsInstance<AiChunk.Token>().joinToString("") { it.text })
            }
        } catch (_: TimeoutCancellationException) {
            Result.Failure(AiFailure.Timeout)
        }

    override fun completeStream(
        messages: List<AiMessage>,
        config: AiConfig,
    ): Flow<AiChunk> =
        // channelFlow — see AnthropicProvider.completeStream for why (execute {}'s block runs on
        // the HTTP engine's dispatcher, a different context than a plain flow{} may collect on).
        channelFlow {
            if (httpConfig.endpoint.isBlank()) {
                send(AiChunk.Failed(AiFailure.NoKey))
                return@channelFlow
            }
            val (system, chatMessages) = buildHttpChatPayload(messages)

            // ponytail: no withTimeout here — see AnthropicProvider.completeStream for why.
            try {
                client
                    .preparePost(httpConfig.endpoint) {
                        httpConfig.originHeader?.let { header(HttpHeaders.Origin, it) }
                        contentType(ContentType.Application.Json)
                        setBody(
                            HttpChatRequest(
                                messages = chatMessages,
                                system = system,
                                mode = httpConfig.mode,
                                maxTokens = config.maxTokens,
                                temperature = config.temperature.toDouble(),
                            ),
                        )
                    }.execute { response ->
                        response.status.toAiFailureOrNull()?.let {
                            send(AiChunk.Failed(it))
                            return@execute
                        }
                        var emittedAny = false
                        parseSseFrames(response.bodyAsChannel().asLineFlow()).collect { payload ->
                            val text =
                                runCatching { sseJson.decodeFromString<HttpChatStreamEvent>(payload) }
                                    .getOrNull()
                                    ?.text
                            if (!text.isNullOrEmpty()) {
                                emittedAny = true
                                send(AiChunk.Token(text))
                            }
                        }
                        if (!emittedAny) send(AiChunk.Failed(AiFailure.EmptyReply))
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                send(AiChunk.Failed(AiFailure.Network))
            }
        }

    private fun buildHttpChatPayload(messages: List<AiMessage>): Pair<String?, List<HttpChatMessage>> {
        val system = messages.firstOrNull { it.role == AiMessage.Role.SYSTEM }?.content?.ifBlank { null }
        val chatMessages =
            messages
                .filter { it.role != AiMessage.Role.SYSTEM }
                .map { HttpChatMessage(role = it.role.toHttpChatRole(), content = it.content) }
        return system to chatMessages
    }

    private fun AiMessage.Role.toHttpChatRole() =
        when (this) {
            AiMessage.Role.USER -> "user"
            AiMessage.Role.ASSISTANT -> "assistant"
            AiMessage.Role.SYSTEM -> "user"
        }
}

@Serializable
private data class HttpChatRequest(
    val messages: List<HttpChatMessage>,
    val system: String?,
    val mode: String?,
    val maxTokens: Int,
    val temperature: Double,
)

@Serializable
private data class HttpChatMessage(
    val role: String,
    val content: String,
)

/** One `data:` payload from the caller's backend — the only shape this provider understands. */
@Serializable
private data class HttpChatStreamEvent(
    val text: String? = null,
)
