package com.siddharth.kmp.location.algo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatchReconciliationTest {

    private fun route(distanceM: Double, confidence: Double) =
        MatchedRoute(distanceM = distanceM, confidence = confidence, pointMatched = listOf(true, true))

    @Test
    fun close_figures_agree_and_present_the_client_distance() {
        val r = MatchReconciliation.reconcile(clientDistanceM = 1000.0, matched = route(1020.0, confidence = 0.9))

        assertEquals(ReconciliationVerdict.AGREE, r.verdict)
        assertEquals(1000.0, r.presentedDistanceM)
    }

    @Test
    fun a_trusted_match_that_disagrees_beyond_tolerance_is_preferred() {
        // Map matching removes a directional bias, so a confident match that reads higher than the
        // client's straight-line figure is exactly the case this exists to correct.
        val r = MatchReconciliation.reconcile(clientDistanceM = 1000.0, matched = route(1200.0, confidence = 0.9))

        assertEquals(ReconciliationVerdict.PREFER_MATCHED, r.verdict)
        assertEquals(1200.0, r.presentedDistanceM)
        assertEquals(1000.0, r.clientDistanceM, "client figure must still be recoverable, never discarded")
    }

    @Test
    fun a_low_confidence_match_never_overrides_the_client_figure() {
        // The real value OSRM returned for a 3-point trace, from this session's verification.
        val r = MatchReconciliation.reconcile(clientDistanceM = 1000.0, matched = route(3000.0, confidence = 5.9e-05))

        assertEquals(ReconciliationVerdict.DISPUTE, r.verdict)
        assertEquals(1000.0, r.presentedDistanceM, "low confidence must not silently override the client figure")
        assertEquals(3000.0, r.matchedDistanceM, "matched figure must still be recoverable, never discarded")
    }

    @Test
    fun wild_disagreement_disputes_even_at_high_confidence() {
        // A confidently-reported figure this far from the client's is more likely a bad or
        // fragmented match than a legitimate bias correction.
        val r = MatchReconciliation.reconcile(clientDistanceM = 1000.0, matched = route(5000.0, confidence = 0.95))

        assertEquals(ReconciliationVerdict.DISPUTE, r.verdict)
        assertEquals(1000.0, r.presentedDistanceM)
    }

    @Test
    fun zero_client_distance_against_a_positive_match_is_a_full_disagreement_not_a_crash() {
        val r = MatchReconciliation.reconcile(clientDistanceM = 0.0, matched = route(500.0, confidence = 0.9))
        assertEquals(1.0, r.differenceRatio)
        assertEquals(ReconciliationVerdict.DISPUTE, r.verdict)
        assertEquals(0.0, r.presentedDistanceM)
    }

    @Test
    fun two_zero_distances_agree_perfectly() {
        val r = MatchReconciliation.reconcile(clientDistanceM = 0.0, matched = route(0.0, confidence = 0.9))
        assertEquals(0.0, r.differenceRatio)
        assertEquals(ReconciliationVerdict.AGREE, r.verdict)
    }

    @Test
    fun rejects_a_negative_client_distance() {
        assertFailsWith<IllegalArgumentException> {
            MatchReconciliation.reconcile(clientDistanceM = -1.0, matched = route(100.0, confidence = 0.9))
        }
    }
}
