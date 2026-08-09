package com.siddharth.kmp.location.algo

import kotlin.math.abs

/**
 * Decide what to present when the client's own distance and a map-matched distance disagree.
 *
 * A map match is a *correction*, not a replacement — the client figure was computed from fixes the
 * app actually recorded and trusted enough to persist, so it is never simply thrown away. This is
 * the one place that decides whether the matched figure has earned the right to override it, and
 * the decision is explicit and testable rather than "whichever number happened to render".
 *
 * Two ways a match must NOT override, both drawn from confirmed OSRM behaviour:
 *  - **Low confidence.** Confidence collapses toward zero on a sparse trace — a real 3-point match
 *    returned `5.9e-05`. A number that low is not "worse evidence", it is the provider saying it
 *    barely fit anything at all.
 *  - **Wild disagreement even at high confidence.** A dense trace can fragment into several
 *    disconnected matchings, or the vehicle may have driven somewhere the road graph does not
 *    model well. A confidently-reported figure that is nowhere near the client's is more likely a
 *    bad fit than a large bias correction, so it is flagged for review rather than trusted blindly.
 */
public object MatchReconciliation {

    public fun reconcile(
        clientDistanceM: Double,
        matched: MatchedRoute,
        agreeToleranceRatio: Double = 0.05,
        disputeRatio: Double = 0.5,
        minConfidence: Double = 0.5,
    ): Reconciliation {
        require(clientDistanceM >= 0.0) { "clientDistanceM must not be negative, got $clientDistanceM" }
        require(agreeToleranceRatio >= 0.0) { "agreeToleranceRatio must not be negative" }
        require(disputeRatio >= agreeToleranceRatio) { "disputeRatio must be >= agreeToleranceRatio" }

        val diffRatio = relativeDifference(clientDistanceM, matched.distanceM)
        val trustworthy = matched.confidence >= minConfidence

        val verdict = when {
            diffRatio <= agreeToleranceRatio -> ReconciliationVerdict.AGREE
            trustworthy && diffRatio <= disputeRatio -> ReconciliationVerdict.PREFER_MATCHED
            else -> ReconciliationVerdict.DISPUTE
        }

        // AGREE and DISPUTE both keep the client figure: on AGREE the two are close enough that
        // overriding buys nothing, and on DISPUTE the matched figure has not earned an override —
        // either it is untrusted (low confidence) or it disagrees more than a plausible bias
        // correction should. PREFER_MATCHED is the only verdict allowed to change what is shown.
        val presentedDistanceM = if (verdict == ReconciliationVerdict.PREFER_MATCHED) {
            matched.distanceM
        } else {
            clientDistanceM
        }

        return Reconciliation(
            verdict = verdict,
            clientDistanceM = clientDistanceM,
            matchedDistanceM = matched.distanceM,
            presentedDistanceM = presentedDistanceM,
            confidence = matched.confidence,
            differenceRatio = diffRatio,
        )
    }

    /**
     * `|a - b| / max(a, b)`, made total: two zero distances agree perfectly, and any distance
     * against a zero disagrees completely, instead of dividing by zero.
     */
    private fun relativeDifference(a: Double, b: Double): Double {
        val denom = maxOf(a, b)
        if (denom <= 0.0) return 0.0
        return abs(a - b) / denom
    }
}

public enum class ReconciliationVerdict { AGREE, PREFER_MATCHED, DISPUTE }

/**
 * The outcome of one reconciliation. Both source figures are always present — see
 * [MatchReconciliation] — so a caller that disagrees with [presentedDistanceM] can still recover
 * whichever figure it dropped.
 */
public data class Reconciliation(
    val verdict: ReconciliationVerdict,
    val clientDistanceM: Double,
    val matchedDistanceM: Double,
    val presentedDistanceM: Double,
    val confidence: Double,
    val differenceRatio: Double,
)
