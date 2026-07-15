package com.siddharth.kmp.ai

/**
 * Detection-ordered [OnDeviceLlm] (ai-engineering.md §7): probes an ordered list of backends and
 * uses the first one that both reports available AND actually produces output. This is how a device
 * escalates ML Kit Gemini Nano (AICore-only) → MediaPipe Gemma (broad device coverage, downloaded on
 * demand) → (nothing → the heuristic tier takes over upstream in DefaultJobIntelligence).
 *
 * The seam is unchanged: callers still see one [OnDeviceLlm]; the ordering lives here.
 */
class CompositeOnDeviceLlm(
    private val backends: List<OnDeviceLlm>,
) : OnDeviceLlm {
    override fun isAvailable(): Boolean = backends.any { it.isAvailable() }

    override val supportsImage: Boolean get() = backends.any { it.supportsImage }

    override suspend fun generate(prompt: String): String? {
        for (backend in backends) {
            if (!backend.isAvailable()) continue
            val result = backend.generate(prompt)
            if (result != null) return result
        }
        return null
    }

    override suspend fun generate(parts: List<LlmPart>): String? {
        val needsImage = parts.any { it is LlmPart.Image }
        for (backend in backends) {
            if (!backend.isAvailable()) continue
            if (needsImage && !backend.supportsImage) continue
            val result = backend.generate(parts)
            if (result != null) return result
        }
        return null
    }
}
