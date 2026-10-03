package com.siddharth.kmp.ai

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Register alongside onDeviceLlmModule() to retain the Gemini Nano tier. */
fun mlKitLlmModule() =
    module {
        single<MlKitLlmFactory> { MlKitLlmFactory { config -> MlKitGenAiOnDeviceLlm(androidContext(), config) } }
    }
