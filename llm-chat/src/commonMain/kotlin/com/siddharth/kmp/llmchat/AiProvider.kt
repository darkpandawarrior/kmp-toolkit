package com.siddharth.kmp.llmchat

import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.getOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/** A cloud (or on-device) chat-completion backend — one turn in, one text reply out. */
interface AiProvider {
    val id: String
    val displayName: String

    /**
     * Runs one turn. Success carries the model's text; failure names why via [AiFailure] — a
     * wrong/missing key, a rate limit, a timeout, any other transport problem, or a reply that
     * came back empty. Never throws for an ordinary provider-side failure.
     */
    suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig = AiConfig(),
    ): AiResult<String>

    suspend fun isAvailable(): Boolean
}

/**
 * Bridge for callers not yet migrated off the pre-[AiResult] `complete()`: collapses every
 * [AiFailure] back to `""`, matching the old `runCatching { }.getOrElse { "" }` behavior.
 *
 * // ponytail: kept for one release so an existing caller's source keeps compiling; drop once
 * // callers read AiResult directly (no consumer in this repo yet — README.md, "no dependents").
 */
@Deprecated(
    "Collapses every AiFailure to \"\" — read complete()'s AiResult and handle the reason instead.",
    ReplaceWith("complete(messages, config).getOrNull().orEmpty()"),
)
suspend fun AiProvider.completeOrBlank(
    messages: List<AiMessage>,
    config: AiConfig = AiConfig(),
): String = complete(messages, config).getOrNull().orEmpty()

data class AiMessage(
    val role: Role,
    val content: String,
) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}

data class AiConfig(
    val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
)

/**
 * Maps a non-2xx HTTP response to the [AiFailure] a caller should see; `null` when [this] is 2xx
 * (the caller then goes on to parse the body). Shared by all three cloud providers so a 401/403
 * and a 429 read the same way regardless of which vendor sent them.
 */
internal fun HttpStatusCode.toAiFailureOrNull(): AiFailure? =
    when {
        isSuccess() -> null
        this == HttpStatusCode.Unauthorized || this == HttpStatusCode.Forbidden -> AiFailure.Unauthorized
        this == HttpStatusCode.TooManyRequests -> AiFailure.RateLimited
        else -> AiFailure.Network
    }
