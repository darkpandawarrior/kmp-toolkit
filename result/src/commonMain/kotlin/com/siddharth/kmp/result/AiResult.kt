package com.siddharth.kmp.result

/**
 * Why an AI call ([com.siddharth.kmp.llmchat.AiProvider.complete] or on-device
 * `OnDeviceLlm.generate`) didn't produce text. Lives in `:result` — a zero-dependency module both
 * the cloud (`:llm-chat`) and on-device (`:ai`) seams already sit downstream of conceptually — so
 * neither invents its own error type and callers handling both seams share one `when`.
 */
enum class AiFailure {
    /** No API key configured for this provider. */
    NoKey,

    /** The key was rejected (401/403). */
    Unauthorized,

    /** The provider is throttling (429). */
    RateLimited,

    /** The call didn't complete before its deadline. */
    Timeout,

    /** Any other transport failure: connectivity, a non-2xx status, or an unparseable response. */
    Network,

    /** On-device only: the model isn't downloaded/resident yet. */
    ModelNotResident,

    /** On-device only: this platform/backend can't run inference at all. */
    NotSupportedOnPlatform,

    /** The call completed but the model returned no usable text. */
    EmptyReply,
}

/** Success-or-typed-failure for an AI call: the model's text, or the [AiFailure] naming why not. */
typealias AiResult<T> = Result<T, AiFailure>
