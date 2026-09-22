package com.siddharth.kmp.location

/** Live tracking-environment signals used to score the *current* fix quality. */
data class QualityInputs(
    val isMock: Boolean = false,
    val isBatteryOptimized: Boolean = false,
    val isPowerSaver: Boolean = false,
    val wasAppKilled: Boolean = false,
    val wasRestarted: Boolean = false,
    val isPermissionMissing: Boolean = false,
    val isGpsOff: Boolean = false,
    val accuracyM: Float = 0f,
    val isStable: Boolean = false,
)

/**
 * Pure, live tracking-quality scorer. Starts at [MAX] and subtracts a fixed penalty per
 * active environmental issue, applies an accuracy-tier penalty, then adds a small bonus when
 * the fix stream is stable. Result is clamped to [[MIN], [MAX]].
 *
 * A *live* scorer: it rates conditions as they happen (for a tracking notification / quality
 * chip), rather than analysing a finished journey after the fact.
 *
 * No Android dependency, so it is fully covered by JVM unit tests.
 */
object TrackingQualityScorer {
    const val MAX = 100
    const val MIN = 0

    const val PENALTY_MOCK = 25
    const val PENALTY_BATTERY_OPT = 15
    const val PENALTY_POWER_SAVER = 15
    const val PENALTY_APP_KILLED = 20
    const val PENALTY_RESTART = 20
    const val PENALTY_PERMISSION = 30
    const val PENALTY_GPS_OFF = 20
    const val STABLE_BONUS = 5

    // Accuracy tiers, in metres. 15 m is roughly a clear-sky consumer GPS fix, 35 m an urban
    // canyon, 75 m a cell/wifi-derived position; past that the fix is barely a position claim.
    private const val ACCURACY_GOOD_M = 15f
    private const val ACCURACY_FAIR_M = 35f
    private const val ACCURACY_POOR_M = 75f

    private const val PENALTY_ACCURACY_FAIR = 5
    private const val PENALTY_ACCURACY_POOR = 10
    private const val PENALTY_ACCURACY_UNUSABLE = 20

    /** Accuracy-tier penalty (m). 0 (unknown) is treated as no penalty. */
    private fun accuracyPenalty(accuracyM: Float): Int =
        when {
            accuracyM <= 0f -> 0
            accuracyM <= ACCURACY_GOOD_M -> 0
            accuracyM <= ACCURACY_FAIR_M -> PENALTY_ACCURACY_FAIR
            accuracyM <= ACCURACY_POOR_M -> PENALTY_ACCURACY_POOR
            else -> PENALTY_ACCURACY_UNUSABLE
        }

    fun score(inputs: QualityInputs): Int {
        var score = MAX
        if (inputs.isMock) score -= PENALTY_MOCK
        if (inputs.isBatteryOptimized) score -= PENALTY_BATTERY_OPT
        if (inputs.isPowerSaver) score -= PENALTY_POWER_SAVER
        if (inputs.wasAppKilled) score -= PENALTY_APP_KILLED
        if (inputs.wasRestarted) score -= PENALTY_RESTART
        if (inputs.isPermissionMissing) score -= PENALTY_PERMISSION
        if (inputs.isGpsOff) score -= PENALTY_GPS_OFF
        score -= accuracyPenalty(inputs.accuracyM)
        if (inputs.isStable) score += STABLE_BONUS
        return score.coerceIn(MIN, MAX)
    }
}
