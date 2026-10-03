package com.siddharth.kmp.ai

/** Optional Gemini Nano backend; supplied by :ai-mlkit on Play-enabled builds. */
fun interface MlKitLlmFactory {
    fun create(config: GenerationConfig?): OnDeviceLlm
}
