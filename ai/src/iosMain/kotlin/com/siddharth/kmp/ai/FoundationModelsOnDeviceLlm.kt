package com.siddharth.kmp.ai

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
class FoundationModelsOnDeviceLlm : OnDeviceLlm {
    override fun isAvailable(): Boolean = false

    override suspend fun generate(prompt: String): String? = null
}
