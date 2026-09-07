package com.siddharth.kmp.ai

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Web/`wasmJs` has no on-device model — same floor as [OnDeviceLlm.jvm.kt]. A caller wanting a real
 * answer in the browser wires its own [CloudOnDeviceLlm] (an `:llm-chat` [com.siddharth.kmp.llmchat.AiProvider]
 * chain reaches every cloud vendor's HTTP API fine from `wasmJs`) instead of this module shipping one
 * itself — this module owns no API keys.
 */
actual fun onDeviceLlmModule(): Module =
    module {
        single<ModelManager> { NoModelManager }
        single<OnDeviceLlm> { UnavailableOnDeviceLlm }
    }
