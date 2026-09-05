package com.siddharth.kmp.ai

import com.siddharth.kmp.llmchat.AiChunk
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.result.AiCapabilities
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * [OnDeviceLlm] backed by an `:llm-chat` [AiProvider] chain — cloud as the on-device fallback tier.
 * A caller appends one of these to [CompositeOnDeviceLlm]'s backend list only when at least one API
 * key exists, so desktop, web (`wasmJs`, which ships no real backend of its own — see
 * `OnDeviceLlm.wasmJs.kt`) and any non-Nano/non-Foundation-Models Android or iOS device still gets a
 * real answer through the same seam, instead of every app hand-maintaining its own always-false stub.
 *
 * [providers] is expected to already be [com.siddharth.kmp.llmchat.buildProviderChain]'s output (or
 * a hand-built list of raw providers, in which case guarding them is the caller's job — this class
 * assumes whatever list it's given is already guard-wrapped, same as [CompositeOnDeviceLlm] assumes
 * of its own backends). [isAvailable] is [providers]`.isNotEmpty()`: a cheap synchronous floor per
 * [OnDeviceLlm]'s contract ("NOT the authoritative runtime check") — a real reachability check needs
 * a network call, so that happens lazily inside [generate]/[generateStream].
 */
class CloudOnDeviceLlm(
    private val providers: List<AiProvider>,
    private val config: AiConfig = AiConfig(),
) : OnDeviceLlm {
    override fun isAvailable(): Boolean = providers.isNotEmpty()

    override suspend fun capabilities(): AiCapabilities =
        firstAvailableProvider()?.capabilities()
            ?: AiCapabilities(streaming = false, multimodal = false, honoredConfigFields = emptySet(), unavailableReason = AiFailure.NoKey)

    /**
     * Detection-ordered, same as [CompositeOnDeviceLlm.tryBackends]: tries each provider in
     * [providers] order, returns the first success, and the last failure when every provider
     * declines or errors. [AiFailure.NoKey] when [providers] is empty — this device has no cloud
     * key configured at all, distinct from a configured-but-failing provider.
     */
    override suspend fun generate(prompt: String): AiResult<String> {
        var lastFailure: AiFailure = AiFailure.NoKey
        for (provider in providers) {
            if (!provider.isAvailable()) continue
            when (val result = provider.complete(listOf(AiMessage(AiMessage.Role.USER, prompt)), config)) {
                is Result.Success -> return result
                is Result.Failure -> lastFailure = result.error
            }
        }
        return Result.Failure(lastFailure)
    }

    /**
     * Streams the first available provider's real SSE token stream (all three HTTP cloud providers
     * genuinely stream — see `:llm-chat`'s `httpCloudCapabilities`).
     *
     * // ponytail: no cross-provider fallback once a stream is chosen, same tradeoff
     * // [CompositeOnDeviceLlm.generateStream] documents — a stream that already showed the user a
     * // few tokens can't un-show them. Add buffered fallback if an empty/failed stream turns out to
     * // be common enough in practice to matter.
     */
    override fun generateStream(prompt: String): Flow<String> =
        flow {
            val provider = firstAvailableProvider() ?: return@flow
            provider.completeStream(listOf(AiMessage(AiMessage.Role.USER, prompt)), config).collect { chunk ->
                if (chunk is AiChunk.Token) emit(chunk.text)
            }
        }

    private suspend fun firstAvailableProvider(): AiProvider? = providers.firstOrNull { it.isAvailable() }
}
