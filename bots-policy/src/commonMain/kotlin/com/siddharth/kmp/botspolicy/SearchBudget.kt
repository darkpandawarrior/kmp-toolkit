package com.siddharth.kmp.botspolicy

/**
 * Search-time budget for [Ismcts.search]: iteration count and wall-clock time, whichever cap is hit
 * first. [rolloutHorizon] is the base rollout depth in plies a caller passes through to [Ismcts.search]
 * — scaling it for domain-specific factors (e.g. table/player count) is the caller's job, not this
 * shell's; this type only carries the budget, it does not interpret it.
 */
data class SearchBudget(
    val maxMillis: Long = 400L,
    val maxIterations: Int = 1500,
    val rolloutHorizon: Int = 12,
)
