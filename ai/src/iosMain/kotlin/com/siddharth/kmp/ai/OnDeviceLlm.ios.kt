package com.siddharth.kmp.ai

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS on-device LLM tier, detection-ordered (ai-engineering.md §7):
 * Apple Foundation Models → MediaPipe Gemma → (falls through to the heuristic tier upstream).
 *
 * Both native backends are Swift/ObjC-only (Foundation Models is Swift; MediaPipe ships an iOS pod),
 * so their real implementations must be bridged from the iosApp Swift layer and injected here. Until
 * then the composite holds the Foundation stub (reports unavailable), so the heuristic tier answers.
 * [ModelManager] is [NoModelManager] on iOS (the OS/pod own model provisioning, not the app).
 */
actual fun onDeviceLlmModule(): Module =
    module {
        single<ModelManager> { NoModelManager }
        single<OnDeviceLlm> {
            CompositeOnDeviceLlm(listOf(FoundationModelsOnDeviceLlm(), MediaPipeOnDeviceLlm()))
        }
    }
