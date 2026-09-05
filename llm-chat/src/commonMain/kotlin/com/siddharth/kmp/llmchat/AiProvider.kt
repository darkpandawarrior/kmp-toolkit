package com.siddharth.kmp.llmchat

import com.siddharth.kmp.result.AiCapabilities
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import com.siddharth.kmp.result.getOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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

    /**
     * Token-by-token variant of [complete] — so a UI can render the reply as it arrives and a Stop
     * action actually cancels the in-flight request (cancelling the collecting coroutine tears down
     * the underlying HTTP call) instead of leaving it running to completion in the background.
     *
     * Default replays [complete]'s result as a single [AiChunk] so any existing implementer keeps
     * compiling unchanged; the three real HTTP-backed providers override this with true SSE
     * streaming. Never throws for an ordinary provider-side failure — ends the flow with
     * [AiChunk.Failed] instead, same as [complete] returning [Result.Failure].
     */
    fun completeStream(
        messages: List<AiMessage>,
        config: AiConfig = AiConfig(),
    ): Flow<AiChunk> =
        flow {
            when (val result = complete(messages, config)) {
                is Result.Success -> emit(AiChunk.Token(result.data))
                is Result.Failure -> emit(AiChunk.Failed(result.error))
            }
        }

    suspend fun isAvailable(): Boolean

    /**
     * Honest self-report for a capability-aware caller: whether this backend genuinely streams
     * tokens and accepts images, which [AiConfig] fields it actually reads, and — when it can't run
     * — the real [AiFailure] reason instead of a bare `false`. Default answer is conservative (no
     * streaming, no honored config) and blames a missing key, matching every real provider's own
     * [isAvailable] gate; a provider whose unavailability means something else overrides this.
     */
    suspend fun capabilities(): AiCapabilities =
        AiCapabilities(
            streaming = false,
            multimodal = false,
            honoredConfigFields = emptySet(),
            unavailableReason = if (isAvailable()) null else AiFailure.NoKey,
        )
}

/**
 * Shared descriptor for the three real HTTP cloud providers — all three stream for real via
 * `completeStream` and honor the same [AiConfig] fields identically ([AiConfig.timeoutMs] only
 * bounds [AiProvider.complete], not `completeStream` — see that override's own comment). One
 * function instead of three copies of the same literal.
 */
internal suspend fun AiProvider.httpCloudCapabilities(): AiCapabilities =
    AiCapabilities(
        streaming = true,
        multimodal = false,
        honoredConfigFields = setOf("maxTokens", "temperature", "timeoutMs"),
        unavailableReason = if (isAvailable()) null else AiFailure.NoKey,
    )

/** One increment of a [AiProvider.completeStream] reply. */
sealed interface AiChunk {
    /** A piece of the model's reply text, in generation order. */
    data class Token(val text: String) : AiChunk

    /** The stream ended in failure — before, or after, some [Token]s already emitted. */
    data class Failed(val reason: AiFailure) : AiChunk
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
    /** Deadline for one [AiProvider.complete] call. Was a hardcoded 5s in every provider. */
    val timeoutMs: Long = 5_000,
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
