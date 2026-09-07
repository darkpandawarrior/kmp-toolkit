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

private const val BASE_URL = "https://api.openai.com/v1/chat/completions"
private const val MODEL = "gpt-4o-mini"

class OpenAiProvider(
    private val apiKey: String,
    engine: HttpClientEngine = httpClientEngine(),
) : AiProvider {
    override val id = "openai"
    override val displayName = "OpenAI (GPT-4o mini)"

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

        val openAiMessages = buildOpenAiMessages(messages)
        return try {
            withTimeout(config.timeoutMs) {
                val response =
                    client.post(BASE_URL) {
                        header(HttpHeaders.Authorization, "Bearer $apiKey")
                        contentType(ContentType.Application.Json)
                        setBody(
                            OpenAiRequest(
                                model = MODEL,
                                messages = openAiMessages,
                                maxTokens = config.maxTokens,
                                temperature = config.temperature.toDouble(),
                            ),
                        )
                    }
                response.status.toAiFailureOrNull()?.let { return@withTimeout Result.Failure(it) }
                val text = response.body<OpenAiResponse>().choices.firstOrNull()?.message?.content
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
            val openAiMessages = buildOpenAiMessages(messages)

            // ponytail: no withTimeout here — see AnthropicProvider.completeStream for why.
            try {
                client
                    .preparePost(BASE_URL) {
                        header(HttpHeaders.Authorization, "Bearer $apiKey")
                        contentType(ContentType.Application.Json)
                        setBody(
                            OpenAiRequest(
                                model = MODEL,
                                messages = openAiMessages,
                                maxTokens = config.maxTokens,
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
                                runCatching { sseJson.decodeFromString<OpenAiStreamChunk>(payload) }
                                    .getOrNull()
                                    ?.choices
                                    ?.firstOrNull()
                                    ?.delta
                                    ?.content
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

    private fun buildOpenAiMessages(messages: List<AiMessage>) =
        messages.map { OpenAiMessage(role = it.role.toOpenAiRole(), content = it.content) }

    private fun AiMessage.Role.toOpenAiRole() =
        when (this) {
            AiMessage.Role.SYSTEM -> "system"
            AiMessage.Role.USER -> "user"
            AiMessage.Role.ASSISTANT -> "assistant"
        }
}

@Serializable
private data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Double,
    val stream: Boolean = false,
)

@Serializable
private data class OpenAiMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class OpenAiResponse(
    val choices: List<OpenAiChoice>,
)

@Serializable
private data class OpenAiChoice(
    val message: OpenAiMessage,
)

/** One `data:` payload from OpenAI's streaming response — `delta.content` is the new text, if any. */
@Serializable
private data class OpenAiStreamChunk(
    val choices: List<OpenAiStreamChoice> = emptyList(),
)

@Serializable
private data class OpenAiStreamChoice(
    val delta: OpenAiStreamDelta = OpenAiStreamDelta(),
)

@Serializable
private data class OpenAiStreamDelta(
    val content: String? = null,
)
