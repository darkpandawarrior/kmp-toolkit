package com.siddharth.kmp.ai

import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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
     * Delegates to the first available (and [OnDeviceLlm.supportsImage]-accepting) backend's OWN
     * [OnDeviceLlm.generateStream] — not the single-emission default this class would otherwise
     * inherit — so a backend with real token streaming (ML Kit GenAI, MediaPipe) actually streams
     * through the one [OnDeviceLlm] every app gets from `onDeviceLlmModule()`, and cancelling the
     * collecting coroutine reaches that backend while it's mid-generation.
     *
     * // ponytail: no cross-backend fallback once a stream is chosen — unlike [generate], which can
     * // retry the next backend after a clean failure, a stream that has already shown the user a
     * // few tokens can't un-show them, so escalating mid-stream would look like a glitch, not a
     * // fallback. Only the pre-flight choice of WHICH backend to stream from escalates. Add
     * // real fallback (buffer until the first emission proves the backend live) if an empty/failed
     * // stream turns out to be common enough in practice to matter.
     */
    override fun generateStream(prompt: String): Flow<String> = chooseBackend { true }?.generateStream(prompt) ?: emptyFlow()

    override fun generateStream(parts: List<LlmPart>): Flow<String> {
        val needsImage = parts.any { it is LlmPart.Image }
        return chooseBackend { !needsImage || it.supportsImage }?.generateStream(parts) ?: emptyFlow()
    }

    private fun chooseBackend(accepts: (OnDeviceLlm) -> Boolean): OnDeviceLlm? =
        backends.firstOrNull { it.isAvailable() && accepts(it) }

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
