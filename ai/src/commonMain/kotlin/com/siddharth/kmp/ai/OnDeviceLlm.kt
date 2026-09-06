package com.siddharth.kmp.ai

import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.module.Module

/**
 * A single-shot on-device text LLM tier. Kept deliberately tiny — one availability gate and one
 * text-in/text-out call — so each platform's actual (ML Kit GenAI on Android, Foundation Models on
 * iOS, unavailable elsewhere) is a thin wrapper, and [DefaultJobIntelligence] never has to know
 * which backend ran.
 *
 * [generate] returns a typed [AiFailure] on any failure — [AiFailure.ModelNotResident] (download
 * it) is distinguishable from [AiFailure.NotSupportedOnPlatform] (this device never can) — so the
 * caller can either degrade to its own heuristic tier or say the right thing to the user.
 */
interface OnDeviceLlm {
    /**
     * Cheap, synchronous floor — true only when the platform *could* run inference. NOT the
     * authoritative runtime check (model residency is async); [generate] still guards internally.
     */
    fun isAvailable(): Boolean

    /** True when this backend accepts an [LlmPart.Image] in [generate]. False = text-only. */
    val supportsImage: Boolean get() = false

    /** Runs [prompt] on-device. Success carries the model's text; failure names why via [AiFailure]. */
    suspend fun generate(prompt: String): AiResult<String>

    /**
     * Multimodal entry point. Default maps a single [LlmPart.Text] onto [generate] (String) so
     * every existing text-only actual (MediaPipe, Foundation Models, jvm) keeps working with zero
     * changes. Backends that accept images (ML Kit GenAI) override this directly.
     */
    suspend fun generate(parts: List<LlmPart>): AiResult<String> {
        val onlyText = parts.singleOrNull() as? LlmPart.Text ?: return Result.Failure(AiFailure.NotSupportedOnPlatform)
        return generate(onlyText.text)
    }

    /**
     * Streaming variant of [generate]. Default replays the single-shot result as one emission so
     * every existing actual keeps working with zero changes; backends with native token streaming
     * (ML Kit GenAI) override this directly.
     */
    fun generateStream(prompt: String): Flow<String> = generateStream(listOf(LlmPart.Text(prompt)))

    fun generateStream(parts: List<LlmPart>): Flow<String> =
        flow {
            val result = generate(parts)
            if (result is Result.Success) emit(result.data)
        }
}

/** The common fallback tier: no on-device model. Desktop/JVM/wasm and any pre-AI device land here. */
object UnavailableOnDeviceLlm : OnDeviceLlm {
    override fun isAvailable(): Boolean = false

    override suspend fun generate(prompt: String): AiResult<String> = Result.Failure(AiFailure.NotSupportedOnPlatform)
}

/**
 * Per-platform Koin bindings for the on-device LLM tier. commonMain's [aiModule] includes this;
 * the actual decides which [OnDeviceLlm] gets bound (ML Kit / Foundation Models / unavailable).
 */
expect fun onDeviceLlmModule(): Module
