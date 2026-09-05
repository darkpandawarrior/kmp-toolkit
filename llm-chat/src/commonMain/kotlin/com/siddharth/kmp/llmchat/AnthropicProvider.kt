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

        val system = messages.firstOrNull { it.role == AiMessage.Role.SYSTEM }?.content ?: ""
        val userMessages =
            messages
                .filter { it.role != AiMessage.Role.SYSTEM }
                .map { AnthropicMessage(role = it.role.toAnthropicRole(), content = it.content) }

        return try {
            // ponytail: fixed 5s ceiling, not derived from AiConfig — add a per-call timeout there
            // if a real completion legitimately needs longer.
            withTimeout(5_000) {
                val response =
                    client.post(BASE_URL) {
                        header("x-api-key", apiKey)
                        header("anthropic-version", ANTHROPIC_VERSION)
                        contentType(ContentType.Application.Json)
                        setBody(
                            AnthropicRequest(
                                model = MODEL,
                                maxTokens = config.maxTokens,
                                system = system.ifBlank { null },
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
