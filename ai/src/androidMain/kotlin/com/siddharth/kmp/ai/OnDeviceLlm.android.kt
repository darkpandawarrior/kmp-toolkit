package com.siddharth.kmp.ai

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android on-device LLM tier, detection-ordered (ai-engineering.md §7):
 * ML Kit Gemini Nano (AICore devices) → MediaPipe Gemma (broad coverage, downloaded on demand) →
 * (falls through to the heuristic tier upstream). [ModelManager] is bound for the settings screen.
 * Gemini Nano participates only when the consumer installs :ai-mlkit and mlKitLlmModule().
 */
actual fun onDeviceLlmModule(): Module =
    module {
        single<MediaPipeModelManager> { MediaPipeModelManager(androidContext()) }
        single<ModelManager> { get<MediaPipeModelManager>() }
        single<OnDeviceLlm> {
            CompositeOnDeviceLlm(
                listOfNotNull(
                    // getOrNull(): the app opts into topK/temperature/maxTokens tuning by defining a
                    // single<GenerationConfig>; absent one, both backends keep their own defaults.
                    getOrNull<MlKitLlmFactory>()?.create(getOrNull<GenerationConfig>()),
                    MediaPipeOnDeviceLlm(androidContext(), get(), getOrNull<GenerationConfig>()),
                ),
            )
        }
    }
