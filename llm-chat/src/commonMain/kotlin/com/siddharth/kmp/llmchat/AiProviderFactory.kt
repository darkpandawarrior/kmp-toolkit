package com.siddharth.kmp.llmchat

/**
 * Builds the provider fallback chain from [config]: on-device first (if enabled and [onDevice] is
 * supplied) → configured cloud providers → [fallback] last. [config.selectedProvider], when it
 * names a cloud provider with a non-blank key, is moved to the front of the cloud group so picking
 * a provider actually tries it first; the remaining configured providers still follow as fallbacks,
 * in the fixed Anthropic > OpenAI > Gemini order (a no-op when nothing is selected, since
 * [ProviderId.OFFLINE_FALLBACK] and [ProviderId.ON_DEVICE] match no cloud entry). [onDevice] and
 * [fallback] are caller-supplied rather than hardcoded — this module ships no on-device LLM or
 * app-specific offline fallback of its own.
 */
fun buildProviderChain(
    config: AiProviderConfig,
    fallback: AiProvider,
    onDevice: AiProvider? = null,
): List<AiProvider> {
    val cloud =
        buildList {
            config.anthropicKey?.takeIf { it.isNotBlank() }?.let { add(ProviderId.ANTHROPIC to AnthropicProvider(it)) }
            config.openAiKey?.takeIf { it.isNotBlank() }?.let { add(ProviderId.OPENAI to OpenAiProvider(it)) }
            config.geminiKey?.takeIf { it.isNotBlank() }?.let { add(ProviderId.GEMINI to GeminiProvider(it)) }
            // sortedByDescending is stable, so ties (everything but the selected one) keep this order.
        }.sortedByDescending { (providerId, _) -> providerId == config.selectedProvider }
            .map { (_, provider) -> provider }

    return buildList {
        if (config.useOnDevice && onDevice != null) add(onDevice)
        addAll(cloud)
        add(fallback)
    }
}

/** Returns the first available provider in [chain], or [fallback] if none report available. */
suspend fun firstAvailable(
    chain: List<AiProvider>,
    fallback: AiProvider,
): AiProvider = chain.firstOrNull { it.isAvailable() } ?: fallback
