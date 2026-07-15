package com.siddharth.kmp.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
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
    private val config: GenerationConfig? = null,
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
                        .setMaxTokens(config?.maxTokens ?: MAX_TOKENS)
                        .apply {
                            // setMaxTopK is the load-time ceiling a session's topK must stay under.
                            config?.topK?.let { setMaxTopK(it) }
                            config?.accelerator?.let { setPreferredBackend(it.toBackend()) }
                        }
                        .build()
                val inference = LlmInference.createFromOptions(context, options)
                try {
                    inference.runPrompt(prompt)?.takeIf { it.isNotBlank() }
                } finally {
                    inference.close()
                }
            }.getOrNull()
        }
    }

    // No sampler override → the simple one-shot path. Otherwise a session carries topK/topP/temperature
    // (the only place MediaPipe's API accepts them). The [config] smart-cast holds: the guard returns
    // when it is null.
    private fun LlmInference.runPrompt(prompt: String): String? {
        if (config?.hasSamplerOverride != true) return generateResponse(prompt)
        val sessionOptions =
            LlmInferenceSession.LlmInferenceSessionOptions
                .builder()
                .apply {
                    config.topK?.let { setTopK(it) }
                    config.topP?.let { setTopP(it) }
                    config.temperature?.let { setTemperature(it) }
                }
                .build()
        val session = LlmInferenceSession.createFromOptions(this, sessionOptions)
        return try {
            session.addQueryChunk(prompt)
            session.generateResponse()
        } finally {
            session.close()
        }
    }

    private companion object {
        const val MAX_TOKENS = 512
    }
}

private fun Accelerator.toBackend(): LlmInference.Backend =
    when (this) {
        Accelerator.CPU -> LlmInference.Backend.CPU
        Accelerator.GPU -> LlmInference.Backend.GPU
        // tasks-genai 0.10.35 exposes no NPU backend — let the runtime choose the best available.
        Accelerator.NPU -> LlmInference.Backend.DEFAULT
    }
