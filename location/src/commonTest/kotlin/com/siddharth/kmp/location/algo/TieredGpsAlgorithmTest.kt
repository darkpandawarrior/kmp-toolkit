package com.siddharth.kmp.location.algo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Runnable check behind the `mileway.v1` port: bucket invariant, jitter suppression, spike
 * exclusion, mock bucketing, resume round-trip, and that a knob actually changes behaviour.
 */
class TieredGpsAlgorithmTest {

    private fun fix(
        lat: Double,
        lng: Double,
        tMs: Long,
        accuracy: Double = 8.0,
        speed: Double = 10.0,
        isMock: Boolean = false,
    ) = Fix(lat = lat, lng = lng, timeMs = tMs, accuracyM = accuracy, speedMps = speed, isMock = isMock)

    // A driving-speed straight line: 5s cadence, ~11.1 m/leg (0.0001 deg lat) so legs clear the
    // driving jitter gate (5 m) comfortably and never trip the abnormal caps.
    private fun drivingTrace(points: Int, startMs: Long = 1_000L): List<Fix> =
        (0 until points).map { i ->
            fix(lat = 12.9000 + i * 0.0001, lng = 77.6000, tMs = startMs + i * 5_000L)
        }

    @Test
    fun bucket_invariant_holds_across_a_mixed_trace() {
        val trace = drivingTrace(15) +
            // a mock leg
            fix(lat = 12.9020, lng = 77.6100, tMs = 76_000L, isMock = true) +
            // a teleport spike (>5km in under 30s)
            fix(lat = 13.5000, lng = 78.2000, tMs = 78_000L)
        val report = AlgorithmHarness.run(TieredGpsAlgorithm(), trace)

        assertTrue(
            report.finalState.invariantHolds(),
            "cleaned must equal original minus abnormal/mock: ${report.finalState}",
        )
    }

    @Test
    fun sub_gate_wander_while_parked_is_suppressed_as_jitter() {
        // Stationary fixes: tiny (~1m) wander, 5s apart, speed reported near zero. Below the
        // driving-band-irrelevant walking jitter gate (2m) and with no movement history, this
        // must be dropped rather than accumulated as travelled distance.
        val trace = listOf(
            fix(lat = 12.9000, lng = 77.6000, tMs = 0L, speed = 0.0),
            fix(lat = 12.90000005, lng = 77.6000, tMs = 5_000L, speed = 0.2),
            fix(lat = 12.90000010, lng = 77.6000, tMs = 10_000L, speed = 0.2),
            fix(lat = 12.90000003, lng = 77.6000, tMs = 15_000L, speed = 0.1),
        )
        val report = AlgorithmHarness.run(TieredGpsAlgorithm(), trace)

        assertEquals(3, report.count(FixVerdict.JITTER), "the three near-zero wanders should be suppressed")
        assertTrue(report.finalState.cleanedM < 1.0, "jitter must not accumulate into cleaned distance")
    }

    @Test
    fun instant_teleport_is_excluded_as_a_spike_not_folded_into_original() {
        val trace = listOf(
            fix(lat = 12.9000, lng = 77.6000, tMs = 0L),
            fix(lat = 12.9010, lng = 77.6010, tMs = 5_000L), // normal ~150m leg
            // >5km jump 5s later: displacement > spikeHardGateM, well past maxPlausibleSpeedMps too
            fix(lat = 13.5000, lng = 78.2000, tMs = 10_000L),
        )
        val report = AlgorithmHarness.run(TieredGpsAlgorithm(), trace)

        assertEquals(1, report.count(FixVerdict.SPIKE))
        assertTrue(report.finalState.spikeM > 5_000.0, "the teleport leg should land in the spike bucket")
        assertEquals(0.0, report.finalState.abnormalM, "a hard spike must not also be double-booked as abnormal")
        assertTrue(
            report.finalState.invariantHolds(),
            "spike must be excluded from originalM per the documented invariant: ${report.finalState}",
        )
    }

    @Test
    fun mock_fixes_are_bucketed_away_from_cleaned() {
        val trace = drivingTrace(4) +
            fix(lat = 12.9010, lng = 77.6100, tMs = 20_000L, isMock = true) +
            fix(lat = 12.9020, lng = 77.6200, tMs = 25_000L, isMock = true)
        val report = AlgorithmHarness.run(TieredGpsAlgorithm(), trace)

        assertTrue(report.finalState.mockM > 0.0, "mock legs should land in the mock bucket")
        assertTrue(report.finalState.cleanedM > 0.0, "real legs should still count")
        assertTrue(report.finalState.invariantHolds())
    }

    @Test
    fun resume_never_invents_distance_across_the_untracked_gap() {
        val trace = drivingTrace(20)
        assertTrue(
            AlgorithmHarness.snapshotRoundTrips({ TieredGpsAlgorithm() }, trace, breakAt = 9),
            "resumed run counted more distance than the uninterrupted run",
        )
    }

    @Test
    fun widening_the_driving_jitter_knob_suppresses_a_leg_the_default_accepts() {
        // Kalman disabled so displacement is an exact haversine of the input coordinates, not a
        // filtered estimate — the point of this test is the knob, not the smoother.
        val trace = listOf(
            fix(lat = 12.9000, lng = 77.6000, tMs = 0L, speed = 0.0), // anchor; speed 0.0 seeds history
            fix(lat = 12.90006, lng = 77.6000, tMs = 5_000L, speed = 10.0), // ~6.7m driving-speed leg
        )
        val base = TieredGpsAlgorithm.milewayV1Profile().with(TieredGpsAlgorithm.FLAG_ENABLE_KALMAN, false)

        val default = AlgorithmHarness.run(TieredGpsAlgorithm(base), trace)
        val widened = AlgorithmHarness.run(
            TieredGpsAlgorithm(base.with(TieredGpsAlgorithm.DrivingJitter.name, 20.0)),
            trace,
        )

        assertTrue(default.finalState.cleanedM > 0.0, "default 5m driving gate should accept a ~6.7m leg")
        assertEquals(0.0, widened.finalState.cleanedM, "a 20m gate should suppress the same leg as jitter")
    }

    @Test
    fun mileway_v1_profile_matches_the_shipped_defaults_exactly() {
        val profile = TieredGpsAlgorithm.milewayV1Profile()
        assertEquals(70.0, profile.d(TieredGpsAlgorithm.MaxPlausibleSpeed))
        assertEquals(50.0, profile.d(TieredGpsAlgorithm.SoftAccuracyCeiling))
        assertEquals(5_000.0, profile.d(TieredGpsAlgorithm.SpikeHardGate))
        assertEquals(5.0, profile.d(TieredGpsAlgorithm.DrivingJitter))
        assertEquals(1.0, profile.d(TieredGpsAlgorithm.KalmanProcessNoise))
        assertTrue(profile.b(TieredGpsAlgorithm.FLAG_ENABLE_KALMAN, default = true), "Kalman is on by default")
    }

    @Test
    fun default_device_envelope_is_a_strict_no_op() {
        // Same trace, same profile, only the envelope instance differs (both are logically
        // "default") — results must be identical, proving the ratio-based scaling is a no-op.
        val trace = drivingTrace(10)
        val a = AlgorithmHarness.run(TieredGpsAlgorithm(envelope = DeviceEnvelope.Default), trace)
        val b = AlgorithmHarness.run(TieredGpsAlgorithm(envelope = DeviceEnvelope()), trace)
        assertEquals(a.finalState.cleanedM, b.finalState.cleanedM)
    }

    @Test
    fun kalman_smoothing_can_be_disabled_via_the_flag() {
        val trace = listOf(
            fix(lat = 12.9000, lng = 77.6000, tMs = 0L, speed = 5.0),
            fix(lat = 12.9002, lng = 77.6000, tMs = 5_000L, speed = 5.0),
        )

        val smoothed = TieredGpsAlgorithm().also { it.reset(SessionContext(0L)) }
        smoothed.process(trace[0])
        val smoothedEmitted = smoothed.process(trace[1]).emitted

        val raw = TieredGpsAlgorithm(
            TieredGpsAlgorithm.milewayV1Profile().with(TieredGpsAlgorithm.FLAG_ENABLE_KALMAN, false),
        ).also { it.reset(SessionContext(0L)) }
        raw.process(trace[0])
        val rawEmitted = raw.process(trace[1]).emitted

        assertEquals(trace[1].lat, rawEmitted?.lat, "Kalman off must pass the coordinate through untouched")
        assertNotEquals(trace[1].lat, smoothedEmitted?.lat, "Kalman on must actually filter the coordinate")
    }
}
