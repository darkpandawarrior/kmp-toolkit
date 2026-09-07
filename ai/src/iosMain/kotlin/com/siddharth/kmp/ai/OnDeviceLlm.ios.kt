package com.siddharth.kmp.ai

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS on-device LLM tier, detection-ordered: Apple Foundation Models → MediaPipe Gemma → (falls
 * through to the heuristic tier upstream).
 *
 * Foundation Models is real once a consumer registers a Swift bridge into
 * [FoundationModelsBridge] (see `ai/ios-bridge/README.md`) — until then [FoundationModelsOnDeviceLlm]
 * degrades to unavailable, same as before a bridge existed. MediaPipe has no bridge yet (still an
 * unconditional stub), so it's next in the chain for when one lands — no chain edits needed then.
 * Either way, an iOS build with no bridge registered falls through to the heuristic tier, same as
 * before this seam existed. [ModelManager] is [NoModelManager] on iOS (the OS/pod own model
 * provisioning, not the app).
 */
actual fun onDeviceLlmModule(): Module =
    module {
        single<ModelManager> { NoModelManager }
        single<OnDeviceLlm> {
            CompositeOnDeviceLlm(listOf(FoundationModelsOnDeviceLlm(), MediaPipeOnDeviceLlm()))
        }
    }
