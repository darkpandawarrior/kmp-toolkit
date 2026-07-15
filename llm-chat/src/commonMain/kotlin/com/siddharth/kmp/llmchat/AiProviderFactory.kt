package com.siddharth.kmp.llmchat

/**
 * Builds the provider fallback chain from [config]: on-device first (if enabled and [onDevice] is
 * supplied) → configured cloud providers (Anthropic/OpenAI/Gemini, in that priority order) →
 * [fallback] last. [onDevice] and [fallback] are caller-supplied rather than hardcoded — this
 * module ships no on-device LLM or app-specific offline fallback of its own.
 */
fun buildProviderChain(
    config: AiProviderConfig,
    fallback: AiProvider,
    onDevice: AiProvider? = null,
): List<AiProvider> =
    buildList {
        if (config.useOnDevice && onDevice != null) add(onDevice)
        config.anthropicKey?.takeIf { it.isNotBlank() }?.let { add(AnthropicProvider(it)) }
        config.openAiKey?.takeIf { it.isNotBlank() }?.let { add(OpenAiProvider(it)) }
        config.geminiKey?.takeIf { it.isNotBlank() }?.let { add(GeminiProvider(it)) }
        add(fallback)
    }

/** Returns the first available provider in [chain], or [fallback] if none report available. */
suspend fun firstAvailable(
    chain: List<AiProvider>,
    fallback: AiProvider,
): AiProvider = chain.firstOrNull { it.isAvailable() } ?: fallback
