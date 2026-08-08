package com.siddharth.kmp.location.algo

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The runnable check behind the algorithm seam.
 *
 * These tests are deliberately about the *contract* every algorithm must honour — dedupe,
 * bucket accounting, the resume rule, knob clamping — not about any one algorithm's tuning.
 * A new algorithm added later should be able to reuse the contract tests verbatim.
 */
class MileageAlgorithmTest {

    // ~111.32 m per 0.001 deg of latitude at the equator; a straight north-bound walk makes the
    // expected distance something that can be reasoned about by hand rather than recorded blind.
    private fun straightLineTrace(points: Int, stepDeg: Double = 0.001, startMs: Long = 1_000L): List<Fix> =
        (0 until points).map { i ->
            Fix(
                lat = 12.9000 + i * stepDeg,
                lng = 77.6000,
                timeMs = startMs + i * 5_000L,
                accuracyM = 8.0,
                speedMps = 10.0,
            )
        }

    @Test
    fun haversine_matches_known_distance() {
        // One thousandth of a degree of latitude is ~111.19 m on a 6371 km sphere.
        val d = haversineMeters(12.9, 77.6, 12.901, 77.6)
        assertTrue(abs(d - 111.19) < 0.5, "expected ~111.19m, got $d")

        // Identical points must be exactly zero, not a rounding smear.
        assertEquals(0.0, haversineMeters(12.9, 77.6, 12.9, 77.6))
    }

    @Test
    fun passthrough_accumulates_each_leg() {
        val trace = straightLineTrace(11) // 10 legs
        val report = AlgorithmHarness.run(PassThroughAlgorithm(), trace)

        assertEquals(11, report.fixCount)
        assertEquals(11, report.finalState.accepted)
        // 10 legs of ~111.19 m
        assertTrue(
            abs(report.finalState.cleanedM - 1111.9) < 5.0,
            "expected ~1111.9m, got ${report.finalState.cleanedM}",
        )
        assertTrue(report.finalState.invariantHolds(), "bucket invariant broken: ${report.finalState}")
    }

    @Test
    fun first_fix_is_an_anchor_not_a_leg() {
        val one = AlgorithmHarness.run(PassThroughAlgorithm(), straightLineTrace(1))
        assertEquals(0.0, one.finalState.cleanedM, "a single fix cannot have travelled anywhere")
    }

    @Test
    fun duplicate_fixes_are_dropped_not_counted() {
        val trace = straightLineTrace(5)
        val withDupes = trace.flatMap { listOf(it, it) } // every fix delivered twice

        val clean = AlgorithmHarness.run(PassThroughAlgorithm(), trace)
        val dupey = AlgorithmHarness.run(PassThroughAlgorithm(), withDupes)

        assertEquals(clean.finalState.cleanedM, dupey.finalState.cleanedM, "duplicates changed the distance")
        assertEquals(5, dupey.count(FixVerdict.DUPLICATE))
    }

    @Test
    fun mock_distance_is_bucketed_away_from_cleaned() {
        val trace = straightLineTrace(6).mapIndexed { i, f -> if (i >= 3) f.copy(isMock = true) else f }
        val report = AlgorithmHarness.run(PassThroughAlgorithm(), trace)

        assertTrue(report.finalState.mockM > 0.0, "mock legs should land in the mock bucket")
        assertTrue(report.finalState.cleanedM > 0.0, "real legs should still count")
        assertTrue(
            report.finalState.invariantHolds(),
            "cleaned must equal original minus mock/abnormal: ${report.finalState}",
        )
    }

    @Test
    fun out_of_bounds_coordinates_are_refused() {
        val algo = PassThroughAlgorithm()
        algo.reset(SessionContext(0L))
        val r = algo.process(Fix(lat = 91.0, lng = 0.0, timeMs = 1L, accuracyM = 5.0))
        assertEquals(FixVerdict.REJECTED_BOUNDS, r.verdict)
        assertNull(r.emitted, "an impossible coordinate must not be persisted")
    }

    @Test
    fun accuracy_ceiling_is_honoured_when_configured() {
        val strict = PassThroughAlgorithm(
            PassThroughAlgorithm.defaultProfile().with(PassThroughAlgorithm.MaxAccuracy.name, 10.0),
        )
        strict.reset(SessionContext(0L))
        val r = strict.process(Fix(12.9, 77.6, timeMs = 1L, accuracyM = 40.0))
        assertEquals(FixVerdict.REJECTED_ACCURACY, r.verdict)
        assertEquals(0.0, r.distanceDeltaM)
    }

    @Test
    fun resume_never_invents_distance_across_the_gap() {
        val trace = straightLineTrace(20)
        assertTrue(
            AlgorithmHarness.snapshotRoundTrips({ PassThroughAlgorithm() }, trace, breakAt = 9),
            "resumed run counted more distance than the uninterrupted run",
        )
    }

    /**
     * Regression for a real defect: the harness originally read `snapshot()` without ever calling
     * `flush()`, so a windowed algorithm's final buffered leg vanished from every replay. It biased
     * comparisons against exactly the algorithms the harness exists to evaluate.
     */
    @Test
    fun harness_flushes_the_window_before_reading_totals() {
        // Holds every leg back by one fix and releases the remainder only on flush().
        class OneFixLagAlgorithm : MileageAlgorithm {
            override val id = AlgorithmId("test-lag")
            override val knobs = emptyList<KnobSpec>()
            private var pending: FixResult? = null
            private var last: Fix? = null
            private var state = AlgorithmState()

            override fun reset(session: SessionContext) {
                pending = null; last = null; state = AlgorithmState()
            }

            override fun process(fix: Fix): FixResult {
                val prev = last
                last = fix
                val d = if (prev == null) 0.0 else haversineMeters(prev.lat, prev.lng, fix.lat, fix.lng)
                val held = pending
                pending = FixResult(FixVerdict.ACCEPTED, fix, d, d, DistanceBucket.CLEANED)
                held?.let { commit(it) }
                return held ?: FixResult(FixVerdict.PAUSED, null, 0.0, 0.0, DistanceBucket.NONE)
            }

            private fun commit(r: FixResult) {
                state = state.copy(
                    originalM = state.originalM + r.distanceDeltaM,
                    cleanedM = state.cleanedM + r.distanceDeltaM,
                    accepted = state.accepted + 1,
                )
            }

            override fun flush(): List<FixResult> =
                pending?.let { pending = null; commit(it); listOf(it) } ?: emptyList()

            override fun snapshot() = state
            override fun restore(state: AlgorithmState) { this.state = state; last = null }
        }

        val trace = straightLineTrace(11)
        val lagged = AlgorithmHarness.run(OneFixLagAlgorithm(), trace)
        val online = AlgorithmHarness.run(PassThroughAlgorithm(), trace)

        assertTrue(
            abs(lagged.finalState.cleanedM - online.finalState.cleanedM) < 0.01,
            "flush() was not drained: windowed=${lagged.finalState.cleanedM} online=${online.finalState.cleanedM}",
        )
    }

    @Test
    fun knob_values_are_clamped_into_their_declared_range() {
        val spec = PassThroughAlgorithm.MaxAccuracy
        val profile = PassThroughAlgorithm.defaultProfile().with(spec.name, 99_999.0)
        assertEquals(spec.max, profile.d(spec), "a wild value must clamp, not propagate")
    }

    @Test
    fun registry_creates_by_id_and_reports_unknown_ids_clearly() {
        val registry = MileageAlgorithmRegistry()
            .register(AlgorithmId.PassThrough) { p, _ -> PassThroughAlgorithm(p) }

        val algo = registry.create(PassThroughAlgorithm.defaultProfile())
        assertEquals(AlgorithmId.PassThrough, algo.id)

        val missing = TuningProfile(AlgorithmId.TieredGps, "nope")
        val error = runCatching { registry.create(missing) }.exceptionOrNull()
        assertTrue(error != null, "an unregistered algorithm must fail loudly, not silently fall back")
    }

    @Test
    fun scorecard_reports_error_against_a_reference_distance() {
        val report = AlgorithmHarness.run(PassThroughAlgorithm(), straightLineTrace(11))
        val card = report.scoreAgainst(truthMeters = 1000.0)
        assertTrue(card.errorM > 0.0, "measured ${card.measuredM} should exceed the 1000m reference")
        assertTrue(card.absErrorPercent in 1.0..50.0, "percent error looks wrong: ${card.absErrorPercent}")
    }
}
