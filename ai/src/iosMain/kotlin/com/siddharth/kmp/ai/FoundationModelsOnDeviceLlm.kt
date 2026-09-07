package com.siddharth.kmp.ai

import com.siddharth.kmp.result.AiCapabilities
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Real actual: Apple Foundation Models, reached via a Swift class conforming to [NativeLlm] and
 * registered into [FoundationModelsBridge] at app startup — see `ai/ios-bridge/README.md`.
 * Kotlin/Native has no `platform.FoundationModels.*` cinterop binding (the framework's
 * Swift-macro-driven `@Generable`/`streamResponse` surface has no ObjC-compatible shape), so this
 * follows the same bridge mechanism Mileway already shipped for `FoundationModelsAnalyzer`/
 * `FoundationModelsLlmGateway`: export a plain Kotlin interface to Swift as an ObjC protocol,
 * implement it in Swift, inject the implementation through a top-level singleton at startup.
 *
 * Until a consumer's `AppDelegate` sets [FoundationModelsBridge.seam]'s `generator`, every method
 * here degrades honestly (unavailable / `NotSupportedOnPlatform` / an empty stream) rather than
 * crashing or faking success — the same behavior this class had as a hard stub, now driven by
 * whether a bridge was actually registered instead of being permanently hardcoded.
 */
object FoundationModelsBridge {
    val seam = InjectableNativeLlm()
}

class FoundationModelsOnDeviceLlm(
    private val bridge: InjectableNativeLlm = FoundationModelsBridge.seam,
) : OnDeviceLlm {
    override fun isAvailable(): Boolean = bridge.isAvailable()

    // GenerationConfig has no call site here — LanguageModelSession's public surface exposes no
    // topK/topP/temperature/maxTokens knobs through this bridge's completion-handler shape (see
    // GenerationConfig's own doc for the full per-backend picture) — so honoredConfigFields stays
    // empty, same as before this bridge existed.
    override suspend fun capabilities(): AiCapabilities =
        AiCapabilities(
            streaming = true,
            multimodal = false,
            honoredConfigFields = emptySet(),
            unavailableReason = if (bridge.isAvailable()) null else AiFailure.NotSupportedOnPlatform,
        )

    override suspend fun generate(prompt: String): AiResult<String> {
        if (!bridge.isAvailable()) return Result.Failure(AiFailure.NotSupportedOnPlatform)
        return bridge.generate(prompt)?.let { Result.Success(it) } ?: Result.Failure(AiFailure.EmptyReply)
    }

    // Real per-token streaming through the injected bridge — cancelling the collecting coroutine
    // calls the native NativeLlmCancelHandle, which the Swift side turns into a real Task.cancel()
    // on LanguageModelSession.streamResponse, so a Stop tap actually stops on-device generation
    // instead of only stopping this Kotlin side from listening. Unavailable (no bridge registered,
    // or the registered one reports off) completes empty rather than crashing.
    override fun generateStream(prompt: String): Flow<String> =
        callbackFlow {
            if (!bridge.isAvailable()) {
                close()
                return@callbackFlow
            }
            val handle =
                bridge.generateStream(
                    prompt,
                    object : NativeLlmStreamCallback {
                        override fun onPartial(text: String) {
                            trySend(text)
                        }

                        override fun onComplete() {
                            close()
                        }

                        override fun onError() {
                            close()
                        }
                    },
                )
            awaitClose { handle.cancel() }
        }

    // ponytail: text-only backend (supportsImage stays the interface's `false` default), so a
    // multi-part/image call is a hard decline — same shape generate(parts)'s inherited default
    // already gives every other text-only backend. Upgrade only if a caller drives multimodal
    // streaming through Foundation Models specifically once Apple exposes that.
    override fun generateStream(parts: List<LlmPart>): Flow<String> {
        val text = parts.singleOrNull() as? LlmPart.Text ?: return emptyFlow()
        return generateStream(text.text)
    }
}
