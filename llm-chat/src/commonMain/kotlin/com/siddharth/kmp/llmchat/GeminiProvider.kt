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

private const val BASE_URL =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent"

// alt=sse: Gemini's streaming variant of the same endpoint, framed as SSE `data:` lines each
// carrying the same GeminiResponse shape as the non-streaming reply (one incremental candidate).
private const val STREAM_URL =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:streamGenerateContent?alt=sse"

class GeminiProvider(
    private val apiKey: String,
    engine: HttpClientEngine = httpClientEngine(),
) : AiProvider {
    override val id = "gemini"
    override val displayName = "Google Gemini (Flash Lite)"

    private val client by lazy {
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    override suspend fun isAvailable() = apiKey.isNotBlank()

    override suspend fun capabilities() = httpCloudCapabilities()

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> {
        if (apiKey.isBlank()) return Result.Failure(AiFailure.NoKey)

        val (systemInstruction, contents) = buildGeminiPayload(messages)

        return try {
            withTimeout(config.timeoutMs) {
                val response =
                    client.post(BASE_URL) {
                        header("x-goog-api-key", apiKey)
                        contentType(ContentType.Application.Json)
                        setBody(
                            GeminiRequest(
                                contents = contents,
                                systemInstruction = systemInstruction,
                                generationConfig =
                                    GeminiGenerationConfig(
                                        maxOutputTokens = config.maxTokens,
                                        temperature = config.temperature.toDouble(),
                                    ),
                            ),
                        )
                    }
                response.status.toAiFailureOrNull()?.let { return@withTimeout Result.Failure(it) }
                val text =
                    response
                        .body<GeminiResponse>()
                        .candidates
                        .firstOrNull()
                        ?.content
                        ?.parts
                        ?.firstOrNull()
                        ?.text
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
        // channelFlow — see AnthropicProvider.completeStream for why (execute {}'s block runs on
        // the HTTP engine's dispatcher, a different context than a plain flow{} may collect on).
        channelFlow {
            if (apiKey.isBlank()) {
                send(AiChunk.Failed(AiFailure.NoKey))
                return@channelFlow
            }
            val (systemInstruction, contents) = buildGeminiPayload(messages)

            // ponytail: no withTimeout here — see AnthropicProvider.completeStream for why.
            try {
                client
                    .preparePost(STREAM_URL) {
                        header("x-goog-api-key", apiKey)
                        contentType(ContentType.Application.Json)
                        setBody(
                            GeminiRequest(
                                contents = contents,
                                systemInstruction = systemInstruction,
                                generationConfig =
                                    GeminiGenerationConfig(
                                        maxOutputTokens = config.maxTokens,
                                        temperature = config.temperature.toDouble(),
                                    ),
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
                                runCatching { sseJson.decodeFromString<GeminiResponse>(payload) }
                                    .getOrNull()
                                    ?.candidates
                                    ?.firstOrNull()
                                    ?.content
                                    ?.parts
                                    ?.firstOrNull()
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

    private fun buildGeminiPayload(messages: List<AiMessage>): Pair<GeminiContent?, List<GeminiContent>> {
        val systemInstruction =
            messages
                .firstOrNull { it.role == AiMessage.Role.SYSTEM }
                ?.let { GeminiContent(parts = listOf(GeminiPart(it.content))) }

        val contents =
            messages.filter { it.role != AiMessage.Role.SYSTEM }.map {
                GeminiContent(
                    role = it.role.toGeminiRole(),
                    parts = listOf(GeminiPart(it.content)),
                )
            }
        return systemInstruction to contents
    }

    private fun AiMessage.Role.toGeminiRole() =
        when (this) {
            AiMessage.Role.USER, AiMessage.Role.SYSTEM -> "user"
            AiMessage.Role.ASSISTANT -> "model"
        }
}

@Serializable
private data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
private data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
private data class GeminiPart(
    val text: String,
)

@Serializable
private data class GeminiGenerationConfig(
    @SerialName("maxOutputTokens") val maxOutputTokens: Int,
    val temperature: Double,
)

@Serializable
private data class GeminiResponse(
    val candidates: List<GeminiCandidate>,
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent,
)
