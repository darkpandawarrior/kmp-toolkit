package com.siddharth.kmp.ai

import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result

/**
 * Detection-ordered [OnDeviceLlm]: probes an ordered list of backends and uses the first one that
 * both reports available AND actually produces output. This is how a device escalates ML Kit
 * Gemini Nano (AICore-only) → MediaPipe Gemma (broad device coverage, downloaded on demand) →
 * (nothing → the caller's own fallback tier).
 *
 * The seam is unchanged: callers still see one [OnDeviceLlm]; the ordering lives here.
 */
class CompositeOnDeviceLlm(
    private val backends: List<OnDeviceLlm>,
) : OnDeviceLlm {
    override fun isAvailable(): Boolean = backends.any { it.isAvailable() }

    override val supportsImage: Boolean get() = backends.any { it.supportsImage }

    override suspend fun generate(prompt: String): AiResult<String> = tryBackends { it.generate(prompt) }

    override suspend fun generate(parts: List<LlmPart>): AiResult<String> {
        val needsImage = parts.any { it is LlmPart.Image }
        return tryBackends(accepts = { !needsImage || it.supportsImage }) { it.generate(parts) }
    }

    /**
     * Runs [call] against each available (and [accepts]-passing) backend in order, returning the
     * first success. When every backend declines or fails, returns the LAST one's failure reason —
     * [AiFailure.NotSupportedOnPlatform] when no backend was even tried (an empty chain, or every
     * backend unavailable/rejected by [accepts]), so an empty chain reads as "this device can't do
     * it" rather than a misleading residency error.
     */
    private suspend fun tryBackends(
        accepts: (OnDeviceLlm) -> Boolean = { true },
        call: suspend (OnDeviceLlm) -> AiResult<String>,
    ): AiResult<String> {
        var lastFailure: AiFailure = AiFailure.NotSupportedOnPlatform
        for (backend in backends) {
            if (!backend.isAvailable() || !accepts(backend)) continue
            val result = call(backend)
            if (result is Result.Success) return result
            lastFailure = (result as Result.Failure).error
        }
        return Result.Failure(lastFailure)
    }
}
