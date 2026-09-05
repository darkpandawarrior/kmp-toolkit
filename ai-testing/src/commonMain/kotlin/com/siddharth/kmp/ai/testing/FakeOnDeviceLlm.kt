package com.siddharth.kmp.ai.testing

import com.siddharth.kmp.ai.LlmPart
import com.siddharth.kmp.ai.OnDeviceLlm
import com.siddharth.kmp.result.AiCapabilities
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * A scriptable [OnDeviceLlm] test double — no real model, no device, no `:ai` backend SDK — so any
 * app can exercise its own AI-branching (a success string, a typed [AiFailure], a chunked stream)
 * without shipping a 500MB model or needing AICore-class hardware just to run its tests.
 *
 * Queue as many outcomes as a test needs with [enqueueSuccess]/[enqueueFailure]/
 * [enqueueStreamChunks] — each [generate]/[generateStream] call consumes the next queued entry, in
 * order. Once the queue runs dry the LAST entry keeps repeating, so a test that only cares about
 * the first call doesn't have to script every call that follows it. An empty queue answers
 * [AiFailure.EmptyReply] (a call) / an empty stream — the same shape a real backend gives back when
 * it has nothing to say, rather than throwing on an un-scripted test.
 */
class FakeOnDeviceLlm(
    override val supportsImage: Boolean = false,
    private var available: Boolean = true,
    private var capabilitiesOverride: AiCapabilities? = null,
) : OnDeviceLlm {
    private val responses = ArrayDeque<AiResult<String>>()
    private val streamChunks = ArrayDeque<List<String>>()

    /** Flips what [isAvailable] (and, absent [setCapabilities], the inherited [capabilities]) reports. */
    fun setAvailable(value: Boolean) {
        available = value
    }

    /** Overrides [capabilities] outright — otherwise it derives from [isAvailable], same as the real default. */
    fun setCapabilities(capabilities: AiCapabilities) {
        capabilitiesOverride = capabilities
    }

    /** The next [generate] call returns [text]. */
    fun enqueueSuccess(text: String) {
        responses.addLast(Result.Success(text))
    }

    /** The next [generate] call fails with [reason]. */
    fun enqueueFailure(reason: AiFailure) {
        responses.addLast(Result.Failure(reason))
    }

    /** The next [generateStream] call emits [chunks] in order, one per element, then completes. */
    fun enqueueStreamChunks(vararg chunks: String) {
        streamChunks.addLast(chunks.toList())
    }

    override fun isAvailable(): Boolean = available

    override suspend fun capabilities(): AiCapabilities = capabilitiesOverride ?: super.capabilities()

    override suspend fun generate(prompt: String): AiResult<String> = nextResponse()

    override suspend fun generate(parts: List<LlmPart>): AiResult<String> = nextResponse()

    override fun generateStream(prompt: String): Flow<String> = nextStream()

    override fun generateStream(parts: List<LlmPart>): Flow<String> = nextStream()

    // Drains in FIFO order while more than one entry is queued; the last entry is peeked (never
    // removed) so it keeps answering every call after the queue would otherwise have run dry.
    private fun nextResponse(): AiResult<String> =
        (if (responses.size > 1) responses.removeFirst() else responses.firstOrNull())
            ?: Result.Failure(AiFailure.EmptyReply)

    private fun nextStream(): Flow<String> =
        (if (streamChunks.size > 1) streamChunks.removeFirst() else streamChunks.firstOrNull())
            .orEmpty()
            .asFlow()
}
