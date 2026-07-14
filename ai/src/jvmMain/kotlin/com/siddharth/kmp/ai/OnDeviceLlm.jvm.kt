package com.siddharth.kmp.ai

import org.koin.core.module.Module
import org.koin.dsl.module

/** Desktop/JVM has no on-device model — the heuristic tier always answers. */
actual fun onDeviceLlmModule(): Module =
    module {
        single<ModelManager> { NoModelManager }
        single<OnDeviceLlm> { UnavailableOnDeviceLlm }
    }
