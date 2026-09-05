package com.siddharth.kmp.ai

/**
 * The seam a platform's native (non-Kotlin/Native-importable) LLM API implements and gets injected
 * through, so a Kotlin `actual` (here, [FoundationModelsOnDeviceLlm]) reaches it without a cinterop
 * binding. Hoisted from Mileway's hand-copied `InjectableDocumentAiAnalyzer`/`InjectableTextGenerator`
 * shape (`core:ai`/`feature:agent` there) so the pattern exists once in the toolkit instead of being
 * re-copied into every consuming app.
 *
 * Deliberately NOT [OnDeviceLlm] itself. Classic ObjC-based Kotlin/Native interop exports a plain
 * Kotlin interface to Swift as an ObjC protocol, and a `suspend fun` on it as a completion-handler
 * method — so [NativeLlm] is kept to the simplest shape a Swift class can conform to (`Boolean`,
 * nullable `String`, a plain callback for streaming), and [FoundationModelsOnDeviceLlm] is the small
 * adapter that turns this into the richer typed [OnDeviceLlm] contract (`AiResult`, a cancellable
 * `Flow`) every other backend already exposes. Matches Mileway's own split between its Swift-facing
 * `TextGenerator` and the app-facing `LlmGateway` a small adapter turns it into.
 */
interface NativeLlm {
    fun isAvailable(): Boolean

    /**
     * Null means "declined" — unavailable, or a runtime failure — never throws. Matches
     * [OnDeviceLlm.generate]'s no-throw contract, and keeps a Swift implementation from having to
     * propagate a native error across the interop boundary.
     */
    suspend fun generate(prompt: String): String?

    /**
     * Streams [prompt] token-by-token through [callback] — real per-token output (Apple's
     * `LanguageModelSession.streamResponse`), not a single-emission replay of [generate]. Returns a
     * [NativeLlmCancelHandle] the Kotlin side calls when its collecting coroutine is cancelled, so
     * Stop/screen-leave actually halts native generation instead of only stopping the Kotlin side
     * from listening — the same contract [MediaPipeOnDeviceLlm]'s Android backend already honors
     * for its own `callbackFlow`.
     */
    fun generateStream(
        prompt: String,
        callback: NativeLlmStreamCallback,
    ): NativeLlmCancelHandle
}

/**
 * Delivered from the native side as [NativeLlm.generateStream] progresses. Exactly one of
 * [onComplete]/[onError] fires, always after the last [onPartial].
 */
interface NativeLlmStreamCallback {
    fun onPartial(text: String)

    fun onComplete()

    fun onError()
}

/** Cancels an in-flight [NativeLlm.generateStream] call. Safe to call more than once. */
fun interface NativeLlmCancelHandle {
    fun cancel()
}

/**
 * Generic delegate-or-degrade injection seam, same shape as Mileway's `InjectableDocumentAiAnalyzer`/
 * `InjectableTextGenerator` — kept in commonMain (not iosMain) so this logic is unit-testable; the
 * iosMain holder ([FoundationModelsBridge]) has nothing left to test once it just forwards here.
 */
class InjectableNativeLlm : NativeLlm {
    /** Set once at app startup (e.g. from AppDelegate) by the platform bridge — see `ai/ios-bridge/README.md`. */
    var generator: NativeLlm? = null

    override fun isAvailable(): Boolean = generator?.isAvailable() ?: false

    override suspend fun generate(prompt: String): String? = generator?.takeIf { it.isAvailable() }?.generate(prompt)

    override fun generateStream(
        prompt: String,
        callback: NativeLlmStreamCallback,
    ): NativeLlmCancelHandle {
        val active = generator?.takeIf { it.isAvailable() }
        if (active == null) {
            // Same "declines rather than calls back into nothing" contract generate() gives:
            // no generator (or an unavailable one) completes the stream empty instead of hanging.
            callback.onComplete()
            return NativeLlmCancelHandle {}
        }
        return active.generateStream(prompt, callback)
    }
}
