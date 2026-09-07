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
 *   to use). `llm-chat` doesn't know or validate the values — the backend does. `null` (the
 *   default) means the `"mode"` key is left off the request body entirely, not sent as
 *   `"mode": null` — a backend with a closed mode allowlist can then treat "no mode" and "an
 *   unrecognized mode" differently, e.g. defaulting the former to ordinary chat and 400ing the
 *   latter.
 * @param route an app-defined string forwarded to the backend as-is (e.g. which screen/page the
 *   caller is on, for a backend that folds that into its system prompt). Same omit-when-null wire
 *   rule as [mode]: left off the request body entirely rather than sent as `"route": null`.
 * @param originHeader sent as this request's `Origin` header when non-null, for a backend that
 *   allow-lists origins as a lightweight check on a public, keyless chat endpoint. Native engines
 *   (OkHttp/Darwin/CIO) send whatever is set here; a real browser (wasmJs) refuses to let a script
 *   override `Origin` — it's a forbidden header per the Fetch spec — so this is a no-op there and
 *   the browser's own Origin goes out instead.
 * @param requireDoneSentinel when true, a stream that closes having emitted at least one [AiChunk.
 *   Token] but never a `data: [DONE]` line is reported as [AiChunk.Failed] (reason [AiFailure.
 *   Network], [AiChunk.Failed.detail] naming the partial length) instead of success — for a backend
 *   whose contract guarantees `[DONE]` as the terminal event. Defaults to false because `[DONE]` is
 *   otherwise optional in this provider's wire contract (see class doc) — a backend that never
 *   sends it and simply closes the connection on a clean finish would otherwise misreport every
 *   successful reply as cut off.
 */
data class HttpChatConfig(
    val endpoint: String,
    val mode: String? = null,
    val route: String? = null,
    val originHeader: String? = null,
    val requireDoneSentinel: Boolean = false,
)

/**
 * [AiProvider] over a caller-supplied HTTP endpoint speaking the same `data: <json>` / `data:
 * [DONE]` SSE contract as [AnthropicProvider]/[OpenAiProvider]/[GeminiProvider] — the shape
 * `cv-siddharth-kmp` and HireSignal were each hand-rolling their own parser for. Every reply frame
 * decodes as [HttpChatStreamEvent]; the backend is expected to emit `{"text":"..."}` per token and
 * close the stream (optionally preceded by a `data: [DONE]` line, discarded rather than decoded)
 * rather than any vendor-specific event shape. See [HttpChatConfig.requireDoneSentinel] for a
 * backend whose `[DONE]` isn't actually optional.
 *
 * Request body: `{"messages":[{"role":"user"|"assistant","content":"..."}],"system"?,"mode"?,
 * "route"?,"maxTokens","temperature"}`. `system`, `mode` and `route` are each omitted entirely when
 * absent (`explicitNulls = false` on the request's [Json]) rather than sent as `"system": null` /
 * `"mode": null` / `"route": null` — required for a backend that validates `mode` against a closed
 * allowlist and would 400 a literal `null`.
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
                // explicitNulls = false: an unset HttpChatRequest.mode/system must be OMITTED from
                // the wire, not sent as `"mode": null` — a backend with a closed mode allowlist
                // (e.g. exactly "compose" | "jd", chat being the absent case) 400s on any `mode`
                // key it doesn't recognize, null included.
                json(Json { ignoreUnknownKeys = true; explicitNulls = false })
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
                                route = httpConfig.route,
                                maxTokens = config.maxTokens,
                                temperature = config.temperature.toDouble(),
                            ),
                        )
                    }.execute { response ->
                        response.status.toAiFailureOrNull()?.let { failure ->
                            val detail = runCatching { response.bodyAsText() }.getOrNull()?.let { extractErrorDetail(it) }
                            val retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.toIntOrNull()
                            send(AiChunk.Failed(failure, detail = detail, retryAfterSeconds = retryAfterSeconds))
                            return@execute
                        }
                        // Inlined rather than the shared parseSseFrames (which discards `[DONE]`
                        // outright) — requireDoneSentinel needs to know whether it was ever seen.
                        var emittedAny = false
                        var receivedChars = 0
                        var sawDone = false
                        response.bodyAsChannel().asLineFlow().collect { line ->
                            if (!line.startsWith("data:")) return@collect
                            val payload = line.removePrefix("data:").trim()
                            if (payload.isEmpty()) return@collect
                            if (payload == "[DONE]") {
                                sawDone = true
                                return@collect
                            }
                            val text =
                                runCatching { sseJson.decodeFromString<HttpChatStreamEvent>(payload) }
                                    .getOrNull()
                                    ?.text
                            if (!text.isNullOrEmpty()) {
                                emittedAny = true
                                receivedChars += text.length
                                send(AiChunk.Token(text))
                            }
                        }
                        when {
                            !emittedAny -> send(AiChunk.Failed(AiFailure.EmptyReply))
                            httpConfig.requireDoneSentinel && !sawDone ->
                                send(
                                    AiChunk.Failed(
                                        AiFailure.Network,
                                        detail = "Stream closed before the completion signal ($receivedChars chars received)",
                                    ),
                                )
                        }
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

/**
 * The server's own wording for a non-2xx response, when it sent one — read as `{"error": "..."}`
 * (the shape a JSON error body speaks) and falling back to the raw body otherwise (plain text, or a
 * shape this doesn't know), so a caller-shown [AiChunk.Failed.detail] still has *something* rather
 * than silently dropping a body that didn't happen to match.
 */
private fun extractErrorDetail(body: String): String? =
    runCatching { sseJson.decodeFromString<HttpChatErrorBody>(body) }.getOrNull()?.error
        ?: body.ifBlank { null }

@Serializable
private data class HttpChatErrorBody(
    val error: String? = null,
)

@Serializable
private data class HttpChatRequest(
    val messages: List<HttpChatMessage>,
    val system: String?,
    val mode: String?,
    val route: String?,
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
