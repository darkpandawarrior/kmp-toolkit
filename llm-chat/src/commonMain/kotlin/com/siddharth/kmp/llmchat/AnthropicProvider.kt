package com.siddharth.kmp.llmchat

import com.siddharth.kmp.network.httpClientEngine
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import io.ktor.client.*
import io.ktor.client.call.*
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
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val BASE_URL = "https://api.anthropic.com/v1/messages"
private const val MODEL = "claude-haiku-4-5-20251001"
private const val ANTHROPIC_VERSION = "2023-06-01"

class AnthropicProvider(
    private val apiKey: String,
    engine: HttpClientEngine = httpClientEngine(),
) : AiProvider {
    override val id = "anthropic"
    override val displayName = "Anthropic (Claude Haiku)"

    private val client by lazy {
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    override suspend fun isAvailable() = apiKey.isNotBlank()

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> {
        if (apiKey.isBlank()) return Result.Failure(AiFailure.NoKey)

        val (system, userMessages) = buildAnthropicPayload(messages)

        return try {
            withTimeout(config.timeoutMs) {
                val response =
                    client.post(BASE_URL) {
                        header("x-api-key", apiKey)
                        header("anthropic-version", ANTHROPIC_VERSION)
                        contentType(ContentType.Application.Json)
                        setBody(
                            AnthropicRequest(
                                model = MODEL,
                                maxTokens = config.maxTokens,
                                system = system,
                                messages = userMessages,
                                temperature = config.temperature.toDouble(),
                            ),
                        )
                    }
                response.status.toAiFailureOrNull()?.let { return@withTimeout Result.Failure(it) }
                val text = response.body<AnthropicResponse>().content.firstOrNull()?.text
                if (text.isNullOrBlank()) Result.Failure(AiFailure.EmptyReply) else Result.Success(text)
            }
        } catch (_: TimeoutCancellationException) {
            Result.Failure(AiFailure.Timeout)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.Failure(AiFailure.Network)
        }
    }

    override fun completeStream(
        messages: List<AiMessage>,
        config: AiConfig,
    ): Flow<AiChunk> =
        // channelFlow, not flow: client.preparePost(...).execute { } runs its block on the HTTP
        // engine's own dispatcher, a different coroutine context than the one collecting this flow.
        // A plain `flow { }` builder enforces same-context emission and throws
        // ("Flow invariant is violated") the moment that context differs — channelFlow's `send` is
        // built for exactly this cross-context-emission shape.
        channelFlow {
            if (apiKey.isBlank()) {
                send(AiChunk.Failed(AiFailure.NoKey))
                return@channelFlow
            }
            val (system, userMessages) = buildAnthropicPayload(messages)

            // ponytail: no withTimeout wrapping the whole stream — a legitimately long reply keeps
            // producing tokens well past a single-call deadline. Stop cancelling the collecting
            // coroutine is what ends the request (and the provider's billing for it) early; add a
            // stall-timeout on individual reads if a hung-but-still-open connection needs detecting.
            try {
                client
                    .preparePost(BASE_URL) {
                        header("x-api-key", apiKey)
                        header("anthropic-version", ANTHROPIC_VERSION)
                        contentType(ContentType.Application.Json)
                        setBody(
                            AnthropicRequest(
                                model = MODEL,
                                maxTokens = config.maxTokens,
                                system = system,
                                messages = userMessages,
                                temperature = config.temperature.toDouble(),
                                stream = true,
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
                                runCatching { sseJson.decodeFromString<AnthropicStreamEvent>(payload) }
                                    .getOrNull()
                                    ?.takeIf { it.type == "content_block_delta" }
                                    ?.delta
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

    private fun buildAnthropicPayload(messages: List<AiMessage>): Pair<String?, List<AnthropicMessage>> {
        val system = messages.firstOrNull { it.role == AiMessage.Role.SYSTEM }?.content?.ifBlank { null }
        val userMessages =
            messages
                .filter { it.role != AiMessage.Role.SYSTEM }
                .map { AnthropicMessage(role = it.role.toAnthropicRole(), content = it.content) }
        return system to userMessages
    }

    private fun AiMessage.Role.toAnthropicRole() =
        when (this) {
            AiMessage.Role.USER -> "user"
            AiMessage.Role.ASSISTANT -> "assistant"
            AiMessage.Role.SYSTEM -> "user"
        }
}

@Serializable
private data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String?,
    val messages: List<AnthropicMessage>,
    val temperature: Double,
    val stream: Boolean = false,
)

@Serializable
private data class AnthropicMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class AnthropicResponse(
    val content: List<AnthropicContent>,
)

@Serializable
private data class AnthropicContent(
    val text: String,
)

/**
 * One `data:` payload from Anthropic's streaming response. Anthropic's stream carries several
 * event `type`s (`message_start`, `content_block_delta`, `message_stop`, `ping`, ...); only
 * `content_block_delta` carries reply text, in `delta.text`. `ignoreUnknownKeys` (configured on
 * this file's [Json]) lets every other event shape decode into this same type with `delta = null`.
 */
@Serializable
private data class AnthropicStreamEvent(
    val type: String,
    val delta: AnthropicStreamDelta? = null,
)

@Serializable
private data class AnthropicStreamDelta(
    val text: String? = null,
)
