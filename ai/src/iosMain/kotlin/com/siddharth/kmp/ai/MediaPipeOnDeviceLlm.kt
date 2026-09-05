package com.siddharth.kmp.ai

import com.siddharth.kmp.common.AppLog
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result

// ponytail: EXPERIMENTAL stub — the SECOND iOS on-device backend behind the OnDeviceLlm seam:
// Apple Foundation Models → **MediaPipe Gemma** → the caller's heuristic tier. MediaPipe LLM
// Inference ships as an iOS CocoaPod (MediaPipeTasksGenAI, Swift/ObjC) with no C surface Kotlin/Native
// can import, so the real availability check + inference must be bridged from the iosApp Swift layer
// and injected through Koin — exactly like FoundationModelsOnDeviceLlm. Until that bridge exists this
// actual reports unavailable, so the composite falls through to the heuristic tier.
//
// Why it still belongs in the chain now: Foundation Models only exists on Apple-Intelligence hardware;
// MediaPipe Gemma (Gemma 3 1B / Gemma-3n, model downloaded on demand) reaches the far larger set of
// older iPhones. Declaring the seam here fixes the detection order so the Swift bridge is a drop-in
// replacement — no chain edits when it lands.
//
// Upgrade path: define a Kotlin interface here, implement it in Swift over MediaPipeTasksGenAI's
// LlmInference (gated on the model file being resident via a downloader with user consent + wifi-only
// default), and bind that impl in onDeviceLlmModule() from MainViewController's Koin start.
@Unimplemented("MediaPipeTasksGenAI ships as a Swift/ObjC-only CocoaPod; needs a Swift bridge from iosApp — see above.")
class MediaPipeOnDeviceLlm : OnDeviceLlm {
    // `by lazy` logs exactly once per instance — see FoundationModelsOnDeviceLlm for why.
    private val warnUnimplementedOnce by lazy {
        AppLog.w("MediaPipeOnDeviceLlm (iOS) has no Swift bridge yet — always reporting unavailable.", tag = "OnDeviceLlm")
    }

    override fun isAvailable(): Boolean {
        warnUnimplementedOnce
        return false
    }

    override suspend fun generate(prompt: String): AiResult<String> = Result.Failure(AiFailure.NotSupportedOnPlatform)
}
