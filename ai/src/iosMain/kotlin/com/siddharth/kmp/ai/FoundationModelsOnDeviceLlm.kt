package com.siddharth.kmp.ai

import com.siddharth.kmp.common.AppLog
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result

// ponytail: EXPERIMENTAL stub — Apple's Foundation Models API (LanguageModelSession + @Generable) is
// Swift-only (the FoundationModels framework exposes no C/ObjC surface Kotlin/Native can import), so
// the real availability check + inference must be bridged from the Swift side in iosApp and injected
// through Koin. Until that bridge exists this actual reports unavailable, so DefaultJobIntelligence
// exercises its heuristic degrade path on every iOS device — including pre-Apple-Intelligence
// hardware, where Foundation Models is absent anyway. Mirrors Mileway's FoundationModelsAnalyzer.
//
// Upgrade path: define a Kotlin interface here, implement it in Swift over LanguageModelSession
// (gated on SystemLanguageModel.availability), and bind that impl in onDeviceLlmModule() from
// MainViewController's Koin start.
//
// generateStream deliberately does NOT override the interface's single-emission default: isAvailable()
// is hardcoded false, so generate() always fails NotSupportedOnPlatform and the default already
// replays that as an empty flow — there's no real generation here yet to stream tokens from or
// cancel mid-flight. When the Swift bridge lands, override generateStream the way
// MediaPipeOnDeviceLlm.android.kt does: a session-shaped handle the Swift side can cancel, so
// LanguageModelSession.streamResponse's partial results reach here and a collector cancelling
// actually stops on-device generation instead of only stopping this Kotlin side from listening.
@Unimplemented("Foundation Models has no Kotlin/Native-importable surface; needs a Swift bridge from iosApp — see above.")
class FoundationModelsOnDeviceLlm : OnDeviceLlm {
    // `by lazy` logs exactly once per instance, on whichever call (isAvailable/generate/capabilities)
    // touches this backend first — no separate flag needed, Kotlin's lazy delegate is already
    // synchronized and idempotent.
    private val warnUnimplementedOnce by lazy {
        AppLog.w("FoundationModelsOnDeviceLlm has no Swift bridge yet — always reporting unavailable.", tag = "OnDeviceLlm")
    }

    override fun isAvailable(): Boolean {
        warnUnimplementedOnce
        return false
    }

    override suspend fun generate(prompt: String): AiResult<String> = Result.Failure(AiFailure.NotSupportedOnPlatform)
}
