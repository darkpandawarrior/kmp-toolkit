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
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> {
        if (apiKey.isBlank()) return Result.Failure(AiFailure.NoKey)

        val openAiMessages =
            messages.map {
                OpenAiMessage(role = it.role.toOpenAiRole(), content = it.content)
            }
        return try {
            // ponytail: fixed 5s ceiling, not derived from AiConfig — add a per-call timeout there
            // if a real completion legitimately needs longer.
            withTimeout(5_000) {
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
