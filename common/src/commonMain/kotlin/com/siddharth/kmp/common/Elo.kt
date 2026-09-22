package com.siddharth.kmp.common

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure ELO ladder math — the rating step and expected-score curve any ranked/competitive feature
 * wants. No clock, no I/O, no domain types: a rating is an Int, a bout is 1-v-opponent, and [step]
 * returns the new rating. Deterministic and testable.
 *
 * Extracted from Kursi's local ranked ladder (Backlog #24). Domain-coupled bits (e.g. a
 * difficulty→opponent-rating table) stay with the caller; only the generic math lives here.
 */
object Elo {
    /** Default K-factor — the maximum single-game swing magnitude. Moderate, for a casual ladder. */
    const val DEFAULT_K: Double = 32.0

    /**
     * The ELO rating scale: a gap of this many points makes the stronger player a 10:1 favourite.
     * 400 is ELO's own constant, not a tuning knob — changing it changes what a rating means.
     */
    const val RATING_SCALE: Double = 400.0

    /** Base of the logistic curve, paired with [RATING_SCALE] by ELO's definition. */
    private const val CURVE_BASE: Double = 10.0

    /** Default upper clamp — ten [RATING_SCALE] steps above the floor, well past any casual ladder. */
    const val DEFAULT_CEILING: Int = 4000

    /** Expected score for [rating] against [opponent] — the logistic ELO curve, in (0,1). */
    fun expectedScore(
        rating: Int,
        opponent: Int,
    ): Double = 1.0 / (1.0 + CURVE_BASE.pow((opponent - rating) / RATING_SCALE))

    /**
     * One ELO step. Returns the NEW rating after a bout vs [opponentRating], where [won] is the
     * actual result. The change is `k * (actual - expected)`, rounded so a win moves at least +1 and
     * a loss at least -1 — STRICTLY monotonic in the result regardless of rounding — then clamped to
     * `[floor, ceiling]`.
     */
    fun step(
        rating: Int,
        opponentRating: Int,
        won: Boolean,
        k: Double = DEFAULT_K,
        floor: Int = 0,
        ceiling: Int = DEFAULT_CEILING,
    ): Int {
        val expected = expectedScore(rating, opponentRating)
        val actual = if (won) 1.0 else 0.0
        val rawDelta = k * (actual - expected)
        val delta =
            if (won) rawDelta.roundToInt().coerceAtLeast(1) else rawDelta.roundToInt().coerceAtMost(-1)
        return (rating + delta).coerceIn(floor, ceiling)
    }
}
