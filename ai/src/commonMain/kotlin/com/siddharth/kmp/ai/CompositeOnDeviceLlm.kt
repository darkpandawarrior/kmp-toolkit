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

    override suspend fun generate(prompt: String): String? {
        for (backend in backends) {
            if (!backend.isAvailable()) continue
            val result = backend.generate(prompt)
            if (result != null) return result
        }
        return null
    }
}
