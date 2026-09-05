package com.siddharth.kmp.result

/**
 * Honest, machine-readable answer to "what can this AI backend actually do right now" — the
 * counterpart to a bare `isAvailable(): Boolean` that collapses "streams tokens", "accepts images"
 * and "why is this off" into a single true/false a caller can't act on or show to a user. Lives in
 * `:result` next to [AiFailure] for the same reason: the on-device (`:ai`) and cloud (`:llm-chat`)
 * seams both report through it rather than inventing their own descriptor shape.
 */
data class AiCapabilities(
    /** True when this backend emits real per-token output, not a single-emission replay. */
    val streaming: Boolean,
    /** True when this backend accepts image input alongside text. */
    val multimodal: Boolean,
    /**
     * Names of the tuning fields (from `GenerationConfig`/`AiConfig`) this backend actually reads.
     * Every field NOT in this set is silently accepted and ignored — see each backend's override.
     */
    val honoredConfigFields: Set<String>,
    /** Why this backend can't run right now — the real reason, not a guess. Null when it can. */
    val unavailableReason: AiFailure?,
)
