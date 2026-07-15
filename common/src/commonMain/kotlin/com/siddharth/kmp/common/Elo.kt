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

    /** Expected score for [rating] against [opponent] — the logistic ELO curve, in (0,1). */
    fun expectedScore(
        rating: Int,
        opponent: Int,
    ): Double = 1.0 / (1.0 + 10.0.pow((opponent - rating) / 400.0))

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
        ceiling: Int = 4000,
    ): Int {
        val expected = expectedScore(rating, opponentRating)
        val actual = if (won) 1.0 else 0.0
        val rawDelta = k * (actual - expected)
        val delta =
            if (won) rawDelta.roundToInt().coerceAtLeast(1) else rawDelta.roundToInt().coerceAtMost(-1)
        return (rating + delta).coerceIn(floor, ceiling)
    }
}
