package com.siddharth.kmp.appshell

/**
 * Pure engagement-gating logic for the in-app review prompt.
 *
 * A prompt is eligible only when ALL hold:
 * - the account is at least [ReviewGateConfig.minAccountAgeDays] old (since first open),
 * - the user has performed at least [ReviewGateConfig.minInteractions] meaningful interactions,
 * - and at least [ReviewGateConfig.cooldownDays] have passed since the last prompt (if ever shown).
 *
 * No platform deps, the counters live in a [ReviewState] supplied by the caller (persisted however
 * the app likes — DataStore, NSUserDefaults, …), so this is trivially unit-testable.
 */
object ReviewEligibility {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun isEligible(
        state: ReviewState,
        nowMillis: Long,
        config: ReviewGateConfig = ReviewGateConfig(),
    ): Boolean {
        if (state.firstOpenMillis <= 0L) return false
        val accountAgeDays = (nowMillis - state.firstOpenMillis) / DAY_MILLIS
        if (accountAgeDays < config.minAccountAgeDays) return false
        if (state.interactionCount < config.minInteractions) return false
        if (state.lastPromptMillis > 0L) {
            val sinceLastPromptDays = (nowMillis - state.lastPromptMillis) / DAY_MILLIS
            if (sinceLastPromptDays < config.cooldownDays) return false
        }
        return true
    }
}

/** Tunable thresholds for the review gate (sane defaults; overridable via app config). */
data class ReviewGateConfig(
    val minAccountAgeDays: Int = 3,
    val minInteractions: Int = 5,
    val cooldownDays: Int = 30,
)

/** Persisted review counters (app-persisted — DataStore/NSUserDefaults/etc. in production). */
data class ReviewState(
    val firstOpenMillis: Long = 0L,
    val interactionCount: Int = 0,
    val lastPromptMillis: Long = 0L,
)
