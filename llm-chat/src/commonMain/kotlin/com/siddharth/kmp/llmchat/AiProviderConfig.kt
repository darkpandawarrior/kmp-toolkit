package com.siddharth.kmp.llmchat

/**
 * Which provider a consumer has selected + the keys needed to build the fallback chain
 * ([buildProviderChain]). `ON_DEVICE` and `OFFLINE_FALLBACK` are the two slots every consumer
 * plugs their own [AiProvider] into ([buildProviderChain]'s `onDevice`/`fallback` params) — this
 * module doesn't ship either implementation. Naming `ANTHROPIC`/`OPENAI`/`GEMINI` here moves that
 * provider to the front of [buildProviderChain]'s cloud chain, ahead of the fixed priority order.
 */
enum class ProviderId { ON_DEVICE, ANTHROPIC, OPENAI, GEMINI, OFFLINE_FALLBACK }

data class AiProviderConfig(
    val selectedProvider: ProviderId = ProviderId.OFFLINE_FALLBACK,
    val anthropicKey: String? = null,
    val openAiKey: String? = null,
    val geminiKey: String? = null,
    val useOnDevice: Boolean = false,
)
