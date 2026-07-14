package com.siddharth.kmp.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ponytail: EXPERIMENTAL — com.google.mediapipe:tasks-genai (LLM Inference, Gemma 3 1B/Gemma-3n).
// Compile-verified only, NOT device-verified: inference needs the model file present (user-downloaded
// via MediaPipeModelManager), which isn't shipped in the repo. This is the SECOND on-device backend
// behind the OnDeviceLlm seam (after ML Kit Gemini Nano) — it covers the far larger set of devices
// AICore doesn't reach.
//
// [isAvailable] gates on the model file existing; [generate] builds a fresh LlmInference per call and
// closes it (the task is CPU/GPU-bound, so it runs off the main thread). Any failure returns null so
// the composite falls through to the heuristic tier.
class MediaPipeOnDeviceLlm(
    private val context: Context,
    private val modelManager: MediaPipeModelManager,
) : OnDeviceLlm {
    override fun isAvailable(): Boolean = modelManager.isReady()

    override suspend fun generate(prompt: String): String? {
        if (!isAvailable()) return null
        return withContext(Dispatchers.Default) {
            runCatching {
                val options =
                    LlmInference.LlmInferenceOptions
                        .builder()
                        .setModelPath(modelManager.modelFile().absolutePath)
                        .setMaxTokens(MAX_TOKENS)
                        .build()
                val inference = LlmInference.createFromOptions(context, options)
                try {
                    inference.generateResponse(prompt)?.takeIf { it.isNotBlank() }
                } finally {
                    inference.close()
                }
            }.getOrNull()
        }
    }

    private companion object {
        const val MAX_TOKENS = 512
    }
}
