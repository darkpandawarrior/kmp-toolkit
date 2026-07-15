package com.siddharth.kmp.ai

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android on-device LLM tier, detection-ordered (ai-engineering.md §7):
 * ML Kit Gemini Nano (AICore devices) → MediaPipe Gemma (broad coverage, downloaded on demand) →
 * (falls through to the heuristic tier upstream). [ModelManager] is bound for the settings screen.
 */
actual fun onDeviceLlmModule(): Module =
    module {
        single<MediaPipeModelManager> { MediaPipeModelManager(androidContext()) }
        single<ModelManager> { get<MediaPipeModelManager>() }
        single<OnDeviceLlm> {
            CompositeOnDeviceLlm(
                listOf(
                    MlKitGenAiOnDeviceLlm(androidContext()),
                    // getOrNull(): the app opts into sampler/accelerator tuning by defining a
                    // single<GenerationConfig>; absent one, MediaPipe keeps its built-in defaults.
                    MediaPipeOnDeviceLlm(androidContext(), get(), getOrNull<GenerationConfig>()),
                ),
            )
        }
    }
