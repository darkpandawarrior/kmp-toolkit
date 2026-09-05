package com.siddharth.kmp.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.siddharth.kmp.result.AiCapabilities
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// ponytail: EXPERIMENTAL — com.google.mediapipe:tasks-genai (LLM Inference, Gemma 3 1B/Gemma-3n).
// Compile-verified only, NOT device-verified: inference needs the model file present (user-downloaded
// via MediaPipeModelManager), which isn't shipped in the repo. This is the SECOND on-device backend
// behind the OnDeviceLlm seam (after ML Kit Gemini Nano) — it covers the far larger set of devices
// AICore doesn't reach.
//
// [isAvailable] gates on the model file existing. [generate]/[generateStream] REUSE one LlmInference
// (the multi-second full model-weight load from disk) across calls — see [getOrCreateInferenceLocked]
// — so only the first prompt after process start pays that cost; each call still gets its own fresh
// LlmInferenceSession (cheap), so no conversation context leaks between unrelated generate() calls.
// `lock` (a Mutex, not `synchronized`) serializes every actual inference: tasks-genai supports exactly
// one in-flight generation per LlmInference, so a second caller must queue behind the first rather
// than race the native session — and Mutex.withLock/lock/unlock are suspend-safe, unlike a monitor.
// ponytail: the cached LlmInference is never proactively evicted while idle (no LRU/idle-timeout), so
// the ~500MB-class model stays resident in memory for the rest of the process once first used — the
// same trade the composite already makes by keeping this whole class alive as a Koin single. Add an
// idle-close timer only if that footprint measurably matters on low-RAM devices.
class MediaPipeOnDeviceLlm(
    private val context: Context,
    private val modelManager: MediaPipeModelManager,
    private val config: GenerationConfig? = null,
) : OnDeviceLlm {
    private val lock = Mutex()
    private var cached: CachedInference? = null

    override fun isAvailable(): Boolean = modelManager.isReady()

    // All of topK/topP/temperature/maxTokens/accelerator are wired — see buildInferenceOptions()/
    // buildSessionOptions() below. ML Kit GenAI also honors topK/temperature/maxTokens now (see
    // MlKitGenAiOnDeviceLlm), but topP/accelerator have no per-request equivalent in that API — this
    // is still the only backend that honors GenerationConfig's FULL shape.
    override suspend fun capabilities(): AiCapabilities =
        AiCapabilities(
            streaming = true,
            multimodal = false,
            honoredConfigFields = setOf("topK", "topP", "temperature", "maxTokens", "accelerator"),
            unavailableReason = if (isAvailable()) null else AiFailure.ModelNotResident,
        )

    override suspend fun generate(prompt: String): AiResult<String> = generateText(prompt)

    // Multimodal entry point: Gemma 3 1B is text-only (supportsImage stays the interface's `false`
    // default), so an image part is a hard decline rather than a silent drop. Every LlmPart.Text is
    // joined — same join CompositeOnDeviceLlm/generateStream(parts) already use below — instead of
    // the OnDeviceLlm default, which accepts only a single Text part and fails everything else.
    override suspend fun generate(parts: List<LlmPart>): AiResult<String> {
        if (parts.any { it is LlmPart.Image }) return Result.Failure(AiFailure.NotSupportedOnPlatform)
        return generateText(parts.filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text })
    }

    private suspend fun generateText(prompt: String): AiResult<String> {
        if (!isAvailable()) return Result.Failure(AiFailure.ModelNotResident)
        val text =
            withContext(Dispatchers.Default) {
                lock.withLock {
                    runCatching { getOrCreateInferenceLocked().runPrompt(prompt) }.getOrNull()
                }
            }?.takeIf { it.isNotBlank() }
        return text?.let { Result.Success(it) } ?: Result.Failure(AiFailure.EmptyReply)
    }

    override fun generateStream(prompt: String): Flow<String> = generateStream(listOf(LlmPart.Text(prompt)))

    // Text-only (no override(parts) multimodal support yet — matches generate()'s current ceiling).
    // Goes through a session (generate()'s simple path skips one when there's no sampler override)
    // because cancelGenerateResponseAsync() — the only handle tasks-genai exposes to actually stop a
    // generation already running — lives on LlmInferenceSession, not LlmInference. When the collecting
    // coroutine is cancelled (Stop tapped, screen left), awaitClose calls it, so the model stops
    // computing the rest of the reply instead of finishing unseen in the background.
    override fun generateStream(parts: List<LlmPart>): Flow<String> =
        callbackFlow {
            val text = parts.filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text }
            if (!isAvailable() || text.isBlank()) {
                close()
                return@callbackFlow
            }
            // Held for the whole streamed generation (not just session setup) — released in
            // awaitClose below, whichever way this flow ends (done, failure, or the collector
            // cancelling). See the class-level comment for why this must serialize with generateText.
            lock.lock()
            var session: LlmInferenceSession? = null
            // runCatching (not a catch block) — matches generate()'s own handling of this same SDK
            // below, and there's no suspension point in here for a real CancellationException to
            // reach anyway (every call is a plain synchronous/native one, not a suspend fun).
            runCatching {
                val newSession = LlmInferenceSession.createFromOptions(getOrCreateInferenceLocked(), buildSessionOptions())
                session = newSession
                newSession.addQueryChunk(text)
                newSession.generateResponseAsync { partial, done ->
                    // isActive (a plain boolean read), not ensureActive() (which throws) — this
                    // lambda runs on tasks-genai's own callback thread, not a coroutine, so throwing
                    // here would be an uncaught exception on a foreign thread rather than a
                    // cooperative cancel. A false reading just means one more partial chunk may have
                    // been in flight when Stop was tapped; cancelGenerateResponseAsync() below is
                    // what actually halts the model.
                    if (isActive) trySend(partial) else newSession.cancelGenerateResponseAsync()
                    if (done) close()
                }
            }.onFailure { close(it) }
            awaitClose {
                session?.cancelGenerateResponseAsync()
                session?.close()
                lock.unlock()
            }
        }.flowOn(Dispatchers.Default)

    /**
     * Must be called while holding [lock]. Reuses the cached [LlmInference] — the expensive,
     * multi-second full model-weight load from disk — across calls instead of rebuilding it every
     * generate()/generateStream(); only rebuilds when the backing model FILE actually changed
     * (deleted + re-downloaded via [ModelManager.delete]/[ModelManager.download], or swapped for a
     * different manifest entry), detected via a cheap path+mtime+length signature rather than
     * hashing the whole ~500MB file on every call.
     */
    private fun getOrCreateInferenceLocked(): LlmInference {
        val file = modelManager.modelFile()
        val signature = ModelSignature(file.absolutePath, file.lastModified(), file.length())
        cached?.let {
            if (it.signature == signature) return it.inference
            it.inference.close()
        }
        return LlmInference.createFromOptions(context, buildInferenceOptions())
            .also { cached = CachedInference(it, signature) }
    }

    private fun buildInferenceOptions(): LlmInference.LlmInferenceOptions =
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

    private fun buildSessionOptions(): LlmInferenceSession.LlmInferenceSessionOptions =
        LlmInferenceSession.LlmInferenceSessionOptions
            .builder()
            .apply {
                config?.topK?.let { setTopK(it) }
                config?.topP?.let { setTopP(it) }
                config?.temperature?.let { setTemperature(it) }
            }
            .build()

    // No sampler override → the simple one-shot path. Otherwise a session carries topK/topP/temperature
    // (the only place MediaPipe's API accepts them).
    private fun LlmInference.runPrompt(prompt: String): String? {
        if (config?.hasSamplerOverride != true) return generateResponse(prompt)
        val session = LlmInferenceSession.createFromOptions(this, buildSessionOptions())
        return try {
            session.addQueryChunk(prompt)
            session.generateResponse()
        } finally {
            session.close()
        }
    }

    private data class ModelSignature(val path: String, val lastModified: Long, val length: Long)

    private class CachedInference(val inference: LlmInference, val signature: ModelSignature)

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
