package com.siddharth.kmp.location.algo

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmentedTripAlgorithmTest {

    private fun drive(points: Int, startMs: Long = 1_000L, stepDeg: Double = 0.0005, intervalMs: Long = 5_000L) =
        (0 until points).map { i ->
            Fix(
                lat = 12.9000 + i * stepDeg,
                lng = 77.6000,
                timeMs = startMs + i * intervalMs,
                accuracyM = 6.0,
                speedMps = 11.0,
            )
        }

    /** Random-walk drift around a fixed point: what a parked car actually produces. */
    private fun parkedDrift(points: Int, startMs: Long, atLat: Double, atLng: Double, intervalMs: Long = 5_000L) =
        (0 until points).map { i ->
            val ring = i % 4
            Fix(
                lat = atLat + when (ring) { 0 -> 0.00004; 2 -> -0.00004; else -> 0.0 },
                lng = atLng + when (ring) { 1 -> 0.00004; 3 -> -0.00004; else -> 0.0 },
                timeMs = startMs + i * intervalMs,
                accuracyM = 8.0,
                speedMps = 0.3,
            )
        }

    // ── parity ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun parity_profile_is_identical_to_the_bare_delegate() {
        val trace = drive(40)
        val bare = AlgorithmHarness.run(TieredGpsAlgorithm(), trace)
        val wrapped = AlgorithmHarness.run(
            SegmentedTripAlgorithm(SegmentedTripAlgorithm.parityProfile()),
            trace,
        )

        assertEquals(
            bare.finalState.cleanedM, wrapped.finalState.cleanedM,
            absoluteTolerance = 0.001,
        )
        assertEquals(bare.finalState.originalM, wrapped.finalState.originalM, absoluteTolerance = 0.001)
    }

    // ── the accuracy-scaled jitter gate ───────────────────────────────────────────────────────────

    @Test
    fun motion_inside_the_error_ellipse_is_rejected() {
        // 4 m steps reported with 40 m accuracy: physically indistinguishable from noise.
        val noisy = (0 until 20).map { i ->
            Fix(
                lat = 12.9 + i * 0.000036, lng = 77.6,
                timeMs = 1000L + i * 5000L, accuracyM = 40.0, speedMps = 0.6,
            )
        }
        val gated = AlgorithmHarness.run(
            SegmentedTripAlgorithm(
                SegmentedTripAlgorithm.defaultProfile()
                    .with(SegmentedTripAlgorithm.FLAG_ENABLE_STOP_SUPPRESSION, false),
            ),
            noisy,
        )
        val ungated = AlgorithmHarness.run(
            SegmentedTripAlgorithm(SegmentedTripAlgorithm.parityProfile()),
            noisy,
        )

        assertTrue(
            gated.finalState.cleanedM < ungated.finalState.cleanedM,
            "accuracy gate should suppress sub-ellipse motion: gated=${gated.finalState.cleanedM} ungated=${ungated.finalState.cleanedM}",
        )
        assertTrue(gated.count(FixVerdict.JITTER) > 0, "expected JITTER verdicts")
    }

    @Test
    fun real_motion_well_outside_the_ellipse_still_counts() {
        val trace = drive(30) // ~55 m steps at 6 m accuracy
        val gated = AlgorithmHarness.run(SegmentedTripAlgorithm(), trace)
        val bare = AlgorithmHarness.run(TieredGpsAlgorithm(), trace)

        assertTrue(
            abs(gated.finalState.cleanedM - bare.finalState.cleanedM) < 1.0,
            "clean driving must not be gated: ${gated.finalState.cleanedM} vs ${bare.finalState.cleanedM}",
        )
    }

    // ── stop detection ────────────────────────────────────────────────────────────────────────────

    @Test
    fun a_confirmed_stop_discards_its_drift() {
        val leg = drive(10)
        val last = leg.last()
        // 60 fixes x 5 s = 300 s parked, well past the 120 s confirm threshold.
        val parked = parkedDrift(60, last.timeMs + 5_000L, last.lat, last.lng)

        val withStops = AlgorithmHarness.run(SegmentedTripAlgorithm(), leg + parked)
        val withoutStops = AlgorithmHarness.run(
            SegmentedTripAlgorithm(SegmentedTripAlgorithm.parityProfile()),
            leg + parked,
        )

        assertTrue(
            withStops.finalState.cleanedM < withoutStops.finalState.cleanedM,
            "parking drift should be discarded: with=${withStops.finalState.cleanedM} without=${withoutStops.finalState.cleanedM}",
        )
        assertTrue(withStops.count(FixVerdict.PAUSED) > 0, "expected PAUSED verdicts during the stop")
    }

    @Test
    fun a_refuted_stop_gives_every_held_metre_back() {
        val leg = drive(8)
        val last = leg.last()
        // Crawl slowly (below stop speed) but steadily away — traffic, not parking.
        val crawl = (1..6).map { i ->
            Fix(
                lat = last.lat + i * 0.00012, lng = last.lng,
                timeMs = last.timeMs + i * 5_000L, accuracyM = 6.0, speedMps = 0.5,
            )
        }
        // Then resume normal speed, which exits the stop radius for certain.
        val resume = (1..6).map { i ->
            Fix(
                lat = crawl.last().lat + i * 0.0005, lng = last.lng,
                timeMs = crawl.last().timeMs + i * 5_000L, accuracyM = 6.0, speedMps = 11.0,
            )
        }
        val trace = leg + crawl + resume

        val segmented = AlgorithmHarness.run(SegmentedTripAlgorithm(), trace)
        val parity = AlgorithmHarness.run(SegmentedTripAlgorithm(SegmentedTripAlgorithm.parityProfile()), trace)

        // Crawling is real travel, so a refuted stop must not lose it.
        assertTrue(
            segmented.finalState.cleanedM > parity.finalState.cleanedM * 0.95,
            "refuted stop lost distance: segmented=${segmented.finalState.cleanedM} parity=${parity.finalState.cleanedM}",
        )
    }

    @Test
    fun a_trip_ending_inside_a_stop_is_resolved_by_flush() {
        val leg = drive(10)
        val last = leg.last()
        // Only 40 s of drift — never reaches stopConfirmSec, so it is unresolved at the end.
        val trailing = parkedDrift(8, last.timeMs + 5_000L, last.lat, last.lng)

        val algo = SegmentedTripAlgorithm()
        algo.reset(SessionContext(startedAtMs = leg.first().timeMs))
        (leg + trailing).forEach { algo.process(it) }
        val terminal = algo.flush()

        assertEquals(1, terminal.size, "flush should resolve the dangling stop")
        assertEquals(FixVerdict.PAUSED, terminal.first().verdict)
        assertTrue(
            terminal.first().reason!!.contains("trip ended inside a stop"),
            "reason should explain the discard: ${terminal.first().reason}",
        )
    }

    // ── speed source ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun derived_speed_is_not_computed_below_the_dt_floor() {
        val algo = SegmentedTripAlgorithm()
        val prev = Fix(12.9, 77.6, timeMs = 1_000L, accuracyM = 5.0)
        // 10 m apart, only 100 ms later => derived speed would be 100 m/s. Doppler says parked.
        val fix = Fix(12.90009, 77.6, timeMs = 1_100L, accuracyM = 5.0, speedMps = 0.2)

        assertEquals(0.2, algo.speedFor(fix, prev), absoluteTolerance = 1e-9)
    }

    @Test
    fun doppler_is_distrusted_when_the_fix_accuracy_is_poor() {
        val algo = SegmentedTripAlgorithm()
        val prev = Fix(12.9, 77.6, timeMs = 0L, accuracyM = 80.0)
        val fix = Fix(12.9005, 77.6, timeMs = 10_000L, accuracyM = 80.0, speedMps = 30.0)

        // accuracy 80 > dopplerMaxAccuracyM 30, so it must fall back to derived (~5.6 m/s).
        val s = algo.speedFor(fix, prev)
        assertTrue(s in 4.0..7.0, "expected derived speed ~5.6 m/s, got $s")
    }

    // ── invariants and resume ─────────────────────────────────────────────────────────────────────

    @Test
    fun bucket_invariant_holds_across_a_mixed_trace() {
        val leg = drive(12)
        val last = leg.last()
        val trace = leg +
            parkedDrift(40, last.timeMs + 5_000L, last.lat, last.lng) +
            listOf(last.copy(timeMs = last.timeMs + 400_000L, isMock = true, lat = last.lat + 0.002))

        val report = AlgorithmHarness.run(SegmentedTripAlgorithm(), trace)
        assertTrue(report.finalState.invariantHolds(), "invariant broken: ${report.finalState}")
    }

    @Test
    fun resume_never_invents_distance_across_the_gap() {
        val trace = drive(30)
        assertTrue(
            AlgorithmHarness.snapshotRoundTrips({ SegmentedTripAlgorithm() }, trace, breakAt = 14),
            "resumed run counted more than the uninterrupted run",
        )
    }

    @Test
    fun snapshot_carries_the_diagnostic_counters() {
        val leg = drive(10)
        val last = leg.last()
        val algo = SegmentedTripAlgorithm()
        algo.reset(SessionContext(leg.first().timeMs))
        (leg + parkedDrift(60, last.timeMs + 5_000L, last.lat, last.lng)).forEach { algo.process(it) }

        val opaque = algo.snapshot().opaque
        assertTrue(
            opaque.containsKey(SegmentedTripAlgorithm.OPAQUE_STOPS_CONFIRMED),
            "counters missing from snapshot: ${opaque.keys}",
        )
        assertTrue(
            (opaque[SegmentedTripAlgorithm.OPAQUE_STOPS_CONFIRMED] ?: 0.0) > 0.0,
            "expected a confirmed stop to be recorded",
        )
    }

    @Test
    fun registry_can_build_it_by_id() {
        val registry = MileageAlgorithmRegistry()
            .register(SegmentedTripAlgorithm.Id) { p, e -> SegmentedTripAlgorithm(p, e) }
        assertEquals(SegmentedTripAlgorithm.Id, registry.create(SegmentedTripAlgorithm.defaultProfile()).id)
    }
}
