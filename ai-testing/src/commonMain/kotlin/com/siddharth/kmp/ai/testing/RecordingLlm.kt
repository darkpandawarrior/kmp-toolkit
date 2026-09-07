package com.siddharth.kmp.ai.testing

import com.siddharth.kmp.ai.LlmPart
import com.siddharth.kmp.ai.OnDeviceLlm
import com.siddharth.kmp.result.AiResult
import kotlinx.coroutines.flow.Flow

/**
 * Wraps another [OnDeviceLlm] (a [FakeOnDeviceLlm] by default) and records every call that reaches
 * it — so a test can assert what an app's OWN caller actually sent (a `CompositeOnDeviceLlm`'s
 * `PromptGuard`-wrapped text, a retry, a prompt the caller built up itself) without inspecting that
 * caller's internals. Every non-recording member (`isAvailable`, `supportsImage`, `capabilities`)
 * delegates straight through to [delegate] via `by` — this class only intercepts the four call
 * entry points to log them before forwarding.
 */
class RecordingLlm(
    private val delegate: OnDeviceLlm = FakeOnDeviceLlm(),
) : OnDeviceLlm by delegate {
    private val _calls = mutableListOf<List<LlmPart>>()

    /** Every call this backend has seen, one entry per [generate]/[generateStream] invocation, in order. */
    val calls: List<List<LlmPart>> get() = _calls

    /** The plain text of the LAST call, when it was a single [LlmPart.Text] (the common `generate(prompt)` case). */
    val lastPrompt: String?
        get() = (_calls.lastOrNull()?.singleOrNull() as? LlmPart.Text)?.text

    override suspend fun generate(prompt: String): AiResult<String> {
        _calls += listOf(LlmPart.Text(prompt))
        return delegate.generate(prompt)
    }

    override suspend fun generate(parts: List<LlmPart>): AiResult<String> {
        _calls += parts
        return delegate.generate(parts)
    }

    override fun generateStream(prompt: String): Flow<String> {
        _calls += listOf(LlmPart.Text(prompt))
        return delegate.generateStream(prompt)
    }

    override fun generateStream(parts: List<LlmPart>): Flow<String> {
        _calls += parts
        return delegate.generateStream(parts)
    }
}
