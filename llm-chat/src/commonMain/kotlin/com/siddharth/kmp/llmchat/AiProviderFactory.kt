package com.siddharth.kmp.llmchat

import com.siddharth.kmp.result.AiCapabilities
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.PromptGuard
import kotlinx.coroutines.flow.Flow

/**
 * Builds the provider fallback chain from [config]: on-device first (if enabled and [onDevice] is
 * supplied) → configured cloud providers → [fallback] last. [config.selectedProvider], when it
 * names a cloud provider with a non-blank key, is moved to the front of the cloud group so picking
 * a provider actually tries it first; the remaining configured providers still follow as fallbacks,
 * in the fixed Anthropic > OpenAI > Gemini order (a no-op when nothing is selected, since
 * [ProviderId.OFFLINE_FALLBACK] and [ProviderId.ON_DEVICE] match no cloud entry). [onDevice] and
 * [fallback] are caller-supplied rather than hardcoded — this module ships no on-device LLM or
 * app-specific offline fallback of its own.
 *
 * Every provider this returns is wrapped in [GuardedAiProvider] — a chat message, or any other
 * free-form USER content, is run through [PromptGuard] before it reaches ANY provider's
 * `complete`/`completeStream`, cloud or on-device, so a caller building a chain through this
 * function can't accidentally skip the guard the way constructing an [AiProvider] directly could.
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
    }.map(::GuardedAiProvider)
}

/**
 * Wraps every USER-role [AiMessage.content] in [delegate]'s request through [PromptGuard] before
 * delegating — SYSTEM/ASSISTANT messages pass through unchanged since they're the app's own
 * instructions or the model's own prior output, not third-party text. [id]/[displayName]/
 * [isAvailable]/[capabilities] pass straight through so wrapping a provider never changes what a
 * caller sees about it (e.g. [buildProviderChain]'s ordering, which reads [id]).
 *
 * // ponytail: an [onDevice] provider that itself routes to `CompositeOnDeviceLlm` gets guarded
 * // twice (once here, once at the on-device seam) — nested delimiters are harmless, just a few
 * // extra bytes; not worth threading an "already guarded" flag through two modules to avoid it.
 */
private class GuardedAiProvider(private val delegate: AiProvider) : AiProvider {
    override val id: String get() = delegate.id
    override val displayName: String get() = delegate.displayName

    override suspend fun isAvailable(): Boolean = delegate.isAvailable()

    override suspend fun capabilities(): AiCapabilities = delegate.capabilities()

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> = delegate.complete(messages.guarded(), config)

    override fun completeStream(
        messages: List<AiMessage>,
        config: AiConfig,
    ): Flow<AiChunk> = delegate.completeStream(messages.guarded(), config)

    private fun List<AiMessage>.guarded(): List<AiMessage> =
        map { message ->
            if (message.role == AiMessage.Role.USER) {
                message.copy(content = PromptGuard.wrap(message.content).text)
            } else {
                message
            }
        }
}

/** Returns the first available provider in [chain], or [fallback] if none report available. */
suspend fun firstAvailable(
    chain: List<AiProvider>,
    fallback: AiProvider,
): AiProvider = chain.firstOrNull { it.isAvailable() } ?: fallback
