package com.siddharth.kmp.location.algo

import com.siddharth.kmp.location.KalmanSmoother
import kotlin.math.max

/**
 * Kalman-smoothed, speed-adaptive GPS cleaning — the algorithm Mileway's `LocationProcessor`
 * actually runs today, extracted onto the [MileageAlgorithm] seam so its thresholds can be swept
 * and its output can be shadow-compared against other algorithms.
 *
 * [milewayV1Profile] reproduces `LocationProcessor`'s hardcoded defaults exactly (same source:
 * `AbnormalDetectionConfig.DEFAULT` + `LocationTrackingConstants`), so this class with that
 * profile and [DeviceEnvelope.Default] is a drop-in numerical match for the shipped app.
 *
 * Two deliberate divergences from the source, both forced by the seam this class conforms to
 * rather than by choice:
 *  - `LocationProcessor.process()` also takes `isPaused`/`suppressSpike`/`motionStill`/
 *    `harshAccel` — caller-supplied context that has no home on [Fix]. [MileageAlgorithm.process]
 *    is deliberately Fix-only (see its doc), so those overrides are out of scope here; only the
 *    context recoverable from the fix stream itself (accuracy, speed, mock, elapsed time) is used.
 *  - A hard "instant teleport" spike is folded into `abnormalDistanceM` *and* `spikeDistanceM` in
 *    the source (double counted, spike-as-audit-tag). [AlgorithmState]'s documented invariant
 *    requires `cleanedM == originalM - abnormalM - mockM` with spike excluded from `originalM`
 *    entirely — so here a spike is its own exclusive [DistanceBucket.SPIKE], never also counted
 *    as [DistanceBucket.ABNORMAL] or folded into `originalM`. Total spike+abnormal+cleaned+mock
 *    distance is unchanged; which bucket a spike lands in is not.
 *
 * [DeviceEnvelope] scales every threshold: the two absolute "ceiling" knobs scale as a ratio
 * against their own default (so [DeviceEnvelope.Default] is a strict no-op regardless of what a
 * tuning profile sets them to), the three multiplier fields apply directly.
 */
public class TieredGpsAlgorithm(
    private val profile: TuningProfile = milewayV1Profile(),
    private val envelope: DeviceEnvelope = DeviceEnvelope.Default,
) : MileageAlgorithm {

    override val id: AlgorithmId = AlgorithmId.TieredGps
    override val knobs: List<KnobSpec> = ALL_KNOBS

    private val enableKalman = profile.b(FLAG_ENABLE_KALMAN, default = true)

    private val hardAccuracyMinM = profile.d(HardAccuracyMin)
    private val hardAccuracyMaxM = profile.d(HardAccuracyMax)
    private val exceptionalStationarySpeedMps = profile.d(ExceptionalStationarySpeed)
    private val walkingMaxMps = profile.d(WalkingMax)
    private val cyclingMaxMps = profile.d(CyclingMax)
    private val stationarySpeedMps = profile.d(StationarySpeed)
    private val minDisplacementFloorM = profile.d(MinDisplacementFloor)
    private val speedHistorySize = profile.d(SpeedHistorySize).toInt().coerceAtLeast(1)
    private val movementHistoryMps = profile.d(MovementHistory)

    private val accuracyRatio = envelope.accuracyCeilingM / DeviceEnvelope.Default.accuracyCeilingM
    private val stationaryAccuracyRatio =
        envelope.stationaryAccuracyCeilingM / DeviceEnvelope.Default.stationaryAccuracyCeilingM

    private val softAccuracyCeilingM = profile.d(SoftAccuracyCeiling) * accuracyRatio
    private val exceptionalStationaryAccuracyM = profile.d(ExceptionalStationaryAccuracy) * stationaryAccuracyRatio

    private val walkingJitterM = profile.d(WalkingJitter) * envelope.minDisplacementMultiplier
    private val cyclingJitterM = profile.d(CyclingJitter) * envelope.minDisplacementMultiplier
    private val drivingJitterM = profile.d(DrivingJitter) * envelope.minDisplacementMultiplier
    private val stationaryJitterM = profile.d(StationaryJitter) * envelope.minDisplacementMultiplier

    private val maxPlausibleSpeedMps = profile.d(MaxPlausibleSpeed) * envelope.abnormalThresholdMultiplier
    private val spikeHardGateM = profile.d(SpikeHardGate) * envelope.abnormalThresholdMultiplier
    private val gapTier5mMps = profile.d(GapTier5mMps) * envelope.abnormalThresholdMultiplier
    private val gapTier1hMps = profile.d(GapTier1hMps) * envelope.abnormalThresholdMultiplier
    private val gapTier6hMps = profile.d(GapTier6hMps) * envelope.abnormalThresholdMultiplier
    private val gapMaxDistanceM = profile.d(GapMaxDistance) * envelope.abnormalThresholdMultiplier

    private val gapMinSec = scaledSec(profile.d(GapMinSec), envelope.gpsIntervalMultiplier)
    private val gap5mSec = scaledSec(profile.d(Gap5mSec), envelope.gpsIntervalMultiplier).coerceAtLeast(gapMinSec)
    private val gap1hSec = scaledSec(profile.d(Gap1hSec), envelope.gpsIntervalMultiplier).coerceAtLeast(gap5mSec)
    private val gap6hSec = scaledSec(profile.d(Gap6hSec), envelope.gpsIntervalMultiplier).coerceAtLeast(gap1hSec)

    private val kalman = KalmanSmoother(profile.d(KalmanProcessNoise))

    private var last: Fix? = null
    private var state = AlgorithmState()
    private val recentSpeedHistory = ArrayDeque<Double>()

    // Live-only accumulators (not part of AlgorithmState): the true count of samples folded into
    // avgSpeedMps, excluding abnormal/mock fixes. Mirrors LocationProcessor's private speedSum/
    // speedCount, which are likewise not part of its persisted TrackStats.
    private var speedSampleSum = 0.0
    private var speedSampleCount = 0

    override fun reset(session: SessionContext) {
        last = null
        state = AlgorithmState()
        recentSpeedHistory.clear()
        speedSampleSum = 0.0
        speedSampleCount = 0
        if (enableKalman) kalman.reset()
    }

    override fun process(fix: Fix): FixResult {
        // Hard coordinate gate — impossible values cannot be real GPS readings.
        if (!fix.lat.inRange(COORD_LAT_MIN, COORD_LAT_MAX) || !fix.lng.inRange(COORD_LNG_MIN, COORD_LNG_MAX)) {
            state = state.copy(rejected = state.rejected + 1)
            return FixResult(FixVerdict.REJECTED_BOUNDS, null, 0.0, 0.0, DistanceBucket.NONE, "out of bounds")
        }
        // Hard accuracy gate — impossibly precise or hopelessly noisy fixes are never persisted.
        if (fix.accuracyM <= hardAccuracyMinM || fix.accuracyM >= hardAccuracyMaxM) {
            state = state.copy(rejected = state.rejected + 1)
            return FixResult(
                FixVerdict.REJECTED_ACCURACY, null, 0.0, 0.0, DistanceBucket.NONE,
                "accuracy ${fix.accuracyM}m outside hard gate [$hardAccuracyMinM, $hardAccuracyMaxM]",
            )
        }

        val speed = fix.speedMps ?: 0.0
        // Soft accuracy gate: persisted but excluded from cleaned distance, unless the device is
        // reliably stationary with recent movement history (drift, not noise).
        val exceptionalStationary =
            speed <= exceptionalStationarySpeedMps &&
                fix.accuracyM < exceptionalStationaryAccuracyM &&
                hasMovementHistory()
        val accuracyGated = fix.accuracyM > softAccuracyCeilingM && !exceptionalStationary

        val prev = last
        // Smooth up front so distance + classification use the filtered position. Kalman off ⇒
        // effFix === fix and the rest of the pipeline is unaffected.
        val effFix =
            if (enableKalman) {
                val (sLat, sLng) = kalman.smooth(fix.lat, fix.lng, fix.accuracyM.toFloat(), fix.timeMs)
                fix.copy(lat = sLat, lng = sLng)
            } else {
                fix
            }

        val displacement = if (prev != null) haversineMeters(prev.lat, prev.lng, effFix.lat, effFix.lng) else 0.0
        val dtSec = if (prev != null) max(1L, (fix.timeMs - prev.timeMs) / 1000L) else 1L
        val impliedSpeed = displacement / dtSec

        // Speed-adaptive jitter suppression: only for normal sampling, never across a time gap,
        // never for a mock fix. A small wander below the speed-tuned gate is dropped while parked
        // unless recent history shows real movement — the anchor (`last`) is kept unchanged so a
        // later genuine move is measured from the last *persisted* point.
        if (prev != null && !fix.isMock && dtSec < gapMinSec) {
            val gate = minDisplacementForSpeed(speed)
            val stationaryMicroJitter = speed < stationarySpeedMps && displacement < stationaryJitterM
            if ((displacement < gate || stationaryMicroJitter) && !hasMovementHistory()) {
                state = state.copy(rejected = state.rejected + 1)
                return FixResult(FixVerdict.JITTER, null, displacement, 0.0, DistanceBucket.NONE, "jitter")
            }
        }

        val abnormal = prev != null && isAbnormal(displacement, impliedSpeed, dtSec)
        val isHardSpike = prev != null && dtSec < gapMinSec && displacement > spikeHardGateM

        // Defaults cover the first fix of a journey: an anchor, not a leg — nothing is measured
        // yet, so it counts toward no bucket (mirrors LocationProcessor's `counted` starting false
        // and never entering the `prev != null` branch for the opening fix).
        var bucket = DistanceBucket.NONE
        var delta = 0.0
        var verdict = FixVerdict.ACCEPTED

        if (prev != null) {
            bucket = when {
                fix.isMock -> DistanceBucket.MOCK
                isHardSpike -> DistanceBucket.SPIKE
                abnormal -> DistanceBucket.ABNORMAL
                accuracyGated -> DistanceBucket.NONE
                else -> DistanceBucket.CLEANED
            }
            delta = if (bucket == DistanceBucket.NONE) 0.0 else displacement
            verdict = when {
                fix.isMock -> FixVerdict.ABNORMAL
                isHardSpike -> FixVerdict.SPIKE
                abnormal -> FixVerdict.ABNORMAL
                accuracyGated -> FixVerdict.REJECTED_ACCURACY
                dtSec >= gapMinSec -> FixVerdict.GAP_RECOVERED
                else -> FixVerdict.ACCEPTED
            }

            // Spike is excluded from originalM by the seam's documented invariant (a teleport was
            // never travelled). Every other bucket, including the accuracy-gated NONE bucket,
            // still counts toward original — it was measured, just not trusted for cleaned.
            val addToOriginal = bucket != DistanceBucket.SPIKE
            state = state.copy(
                originalM = state.originalM + (if (addToOriginal) displacement else 0.0),
                cleanedM = if (bucket == DistanceBucket.CLEANED) state.cleanedM + delta else state.cleanedM,
                abnormalM = if (bucket == DistanceBucket.ABNORMAL) state.abnormalM + delta else state.abnormalM,
                mockM = if (bucket == DistanceBucket.MOCK) state.mockM + delta else state.mockM,
                spikeM = if (bucket == DistanceBucket.SPIKE) state.spikeM + delta else state.spikeM,
                consecutiveNormal = if (abnormal) 0 else state.consecutiveNormal + 1,
            )
        }

        if (!abnormal && !fix.isMock) {
            speedSampleSum += speed
            speedSampleCount++
        }

        // Rolling movement-history window, recorded for every fix that reached this point
        // (bounds/hard-accuracy/jitter all return earlier and never touch it).
        recentSpeedHistory.addLast(speed)
        if (recentSpeedHistory.size > speedHistorySize) recentSpeedHistory.removeFirst()

        state = state.copy(
            accepted = state.accepted + 1,
            avgSpeedMps = if (speedSampleCount > 0) speedSampleSum / speedSampleCount else 0.0,
            maxSpeedMps = if (!abnormal && !fix.isMock) maxOf(state.maxSpeedMps, speed) else state.maxSpeedMps,
            lastFix = effFix,
        )
        last = effFix

        return FixResult(
            verdict = verdict,
            emitted = effFix,
            displacementM = displacement,
            distanceDeltaM = delta,
            bucket = bucket,
        )
    }

    override fun snapshot(): AlgorithmState = state

    override fun restore(state: AlgorithmState) {
        this.state = state
        // The resumed segment must never bridge the untracked gap: the first fix after resume is
        // an anchor, not a leg, even though the snapshot carries a lastFix.
        this.last = null
        recentSpeedHistory.clear()
        // The exact non-abnormal/non-mock sample count isn't part of AlgorithmState; weight by
        // `accepted` to keep the running average continuous — the same approximation
        // LocationProcessor's own TrackStats-resume path makes.
        speedSampleCount = state.accepted
        speedSampleSum = state.avgSpeedMps * state.accepted
        // Pause→resume must not let a stale filter state bleed into the resumed segment.
        if (enableKalman) kalman.reset()
    }

    /** Minimum displacement (m) a fix must cover to escape jitter suppression, by speed band. */
    private fun minDisplacementForSpeed(speedMps: Double): Double {
        val bandJitter = when {
            speedMps < walkingMaxMps -> walkingJitterM
            speedMps < cyclingMaxMps -> cyclingJitterM
            else -> drivingJitterM
        }
        // The user-set floor only ever raises the gate; 0.0 (default) is a no-op.
        return max(bandJitter, minDisplacementFloorM)
    }

    /** True when the recent window shows sustained movement, so a small step isn't jitter. */
    private fun hasMovementHistory(): Boolean =
        recentSpeedHistory.isNotEmpty() && recentSpeedHistory.average() >= movementHistoryMps

    /**
     * Classify a step as abnormal. For normal sampling (<gapMinSec) a hard-gate jump is an
     * instant teleport and any implied speed above the plausible cap is a spike. For recognised
     * gaps the cap is relaxed by tier; beyond the longest tier a flat distance gate replaces the
     * speed test.
     */
    private fun isAbnormal(displacement: Double, impliedSpeed: Double, dtSec: Long): Boolean =
        when {
            dtSec < gapMinSec -> displacement > spikeHardGateM || impliedSpeed > maxPlausibleSpeedMps
            dtSec <= gap5mSec -> impliedSpeed > gapTier5mMps
            dtSec <= gap1hSec -> impliedSpeed > gapTier1hMps
            dtSec <= gap6hSec -> impliedSpeed > gapTier6hMps
            else -> displacement > gapMaxDistanceM
        }

    public companion object {
        public const val FLAG_ENABLE_KALMAN: String = "enableKalman"

        // ponytail: lat/lng range (-90..90 / -180..180) stays a plain constant, not a KnobSpec —
        // it's the mathematical definition of a coordinate, not a product tuning value. Every
        // *tunable* threshold below is a knob.
        private const val COORD_LAT_MIN = -90.0
        private const val COORD_LAT_MAX = 90.0
        private const val COORD_LNG_MIN = -180.0
        private const val COORD_LNG_MAX = 180.0

        public val MaxPlausibleSpeed: KnobSpec = KnobSpec(
            name = "maxPlausibleSpeedMps", default = 70.0, min = 10.0, max = 200.0, step = 5.0, unit = "m/s",
            description = "Implied speed above this during normal sampling is a spike.",
        )
        public val SoftAccuracyCeiling: KnobSpec = KnobSpec(
            name = "softAccuracyCeilingM", default = 50.0, min = 0.0, max = 500.0, step = 5.0, unit = "m",
            description = "Fixes worse than this are persisted but excluded from cleaned distance.",
        )
        public val MinDisplacementFloor: KnobSpec = KnobSpec(
            name = "minDisplacementFloorM", default = 0.0, min = 0.0, max = 50.0, step = 1.0, unit = "m",
            description = "User-set floor over the per-band jitter gate.",
        )
        public val HardAccuracyMin: KnobSpec = KnobSpec(
            name = "hardAccuracyMinM", default = 0.1, min = 0.0, max = 5.0, step = 0.1, unit = "m",
            description = "Accuracy at or below this is impossibly precise; the fix is rejected outright.",
        )
        public val HardAccuracyMax: KnobSpec = KnobSpec(
            name = "hardAccuracyMaxM", default = 250.0, min = 10.0, max = 1000.0, step = 10.0, unit = "m",
            description = "Accuracy at or above this is hopelessly noisy; the fix is rejected outright.",
        )
        public val ExceptionalStationarySpeed: KnobSpec = KnobSpec(
            name = "exceptionalStationarySpeedMps", default = 0.1, min = 0.0, max = 5.0, step = 0.1, unit = "m/s",
        )
        public val ExceptionalStationaryAccuracy: KnobSpec = KnobSpec(
            name = "exceptionalStationaryAccuracyM", default = 20.0, min = 0.0, max = 100.0, step = 1.0, unit = "m",
        )
        public val WalkingMax: KnobSpec = KnobSpec(
            name = "walkingMaxMps", default = 2.5, min = 0.5, max = 10.0, step = 0.5, unit = "m/s",
        )
        public val CyclingMax: KnobSpec = KnobSpec(
            name = "cyclingMaxMps", default = 7.0, min = 1.0, max = 20.0, step = 0.5, unit = "m/s",
        )
        public val WalkingJitter: KnobSpec = KnobSpec(
            name = "walkingJitterM", default = 2.0, min = 0.0, max = 20.0, step = 0.5, unit = "m",
        )
        public val CyclingJitter: KnobSpec = KnobSpec(
            name = "cyclingJitterM", default = 3.0, min = 0.0, max = 20.0, step = 0.5, unit = "m",
        )
        public val DrivingJitter: KnobSpec = KnobSpec(
            name = "drivingJitterM", default = 5.0, min = 0.0, max = 30.0, step = 0.5, unit = "m",
        )
        public val StationarySpeed: KnobSpec = KnobSpec(
            name = "stationarySpeedMps", default = 1.2, min = 0.0, max = 5.0, step = 0.1, unit = "m/s",
        )
        public val StationaryJitter: KnobSpec = KnobSpec(
            name = "stationaryJitterM", default = 1.2, min = 0.0, max = 10.0, step = 0.1, unit = "m",
        )
        public val SpeedHistorySize: KnobSpec = KnobSpec(
            name = "speedHistorySize", default = 5.0, min = 1.0, max = 20.0, step = 1.0, unit = "samples",
        )
        public val MovementHistory: KnobSpec = KnobSpec(
            name = "movementHistoryMps", default = 1.5, min = 0.0, max = 10.0, step = 0.1, unit = "m/s",
        )
        public val SpikeHardGate: KnobSpec = KnobSpec(
            name = "spikeHardGateM", default = 5_000.0, min = 500.0, max = 20_000.0, step = 500.0, unit = "m",
            description = "Displacement above this during normal sampling is an instant teleport.",
        )
        public val GapMinSec: KnobSpec = KnobSpec(
            name = "gapMinSec", default = 30.0, min = 5.0, max = 120.0, step = 5.0, unit = "s",
            description = "Below this, sampling is 'normal'; at or above, a recovery tier applies.",
        )
        public val Gap5mSec: KnobSpec = KnobSpec(
            name = "gap5mSec", default = 300.0, min = 60.0, max = 1_800.0, step = 60.0, unit = "s",
        )
        public val Gap1hSec: KnobSpec = KnobSpec(
            name = "gap1hSec", default = 3_600.0, min = 600.0, max = 14_400.0, step = 300.0, unit = "s",
        )
        public val Gap6hSec: KnobSpec = KnobSpec(
            name = "gap6hSec", default = 21_600.0, min = 3_600.0, max = 86_400.0, step = 1_800.0, unit = "s",
        )
        public val GapTier5mMps: KnobSpec = KnobSpec(
            name = "gapTier5mMps", default = 150.0, min = 10.0, max = 400.0, step = 10.0, unit = "m/s",
        )
        public val GapTier1hMps: KnobSpec = KnobSpec(
            name = "gapTier1hMps", default = 100.0, min = 10.0, max = 400.0, step = 10.0, unit = "m/s",
        )
        public val GapTier6hMps: KnobSpec = KnobSpec(
            name = "gapTier6hMps", default = 60.0, min = 5.0, max = 400.0, step = 5.0, unit = "m/s",
        )
        public val GapMaxDistance: KnobSpec = KnobSpec(
            name = "gapMaxDistanceM", default = 10_000.0, min = 1_000.0, max = 100_000.0, step = 1_000.0, unit = "m",
            description = "Beyond the longest gap tier, a flat distance gate replaces the speed test.",
        )
        public val KalmanProcessNoise: KnobSpec = KnobSpec(
            name = "kalmanProcessNoiseMps", default = 1.0, min = 0.0, max = 20.0, step = 0.5, unit = "m/s",
        )

        public val ALL_KNOBS: List<KnobSpec> = listOf(
            MaxPlausibleSpeed, SoftAccuracyCeiling, MinDisplacementFloor, HardAccuracyMin, HardAccuracyMax,
            ExceptionalStationarySpeed, ExceptionalStationaryAccuracy, WalkingMax, CyclingMax, WalkingJitter,
            CyclingJitter, DrivingJitter, StationarySpeed, StationaryJitter, SpeedHistorySize, MovementHistory,
            SpikeHardGate, GapMinSec, Gap5mSec, Gap1hSec, Gap6hSec, GapTier5mMps, GapTier1hMps, GapTier6hMps,
            GapMaxDistance, KalmanProcessNoise,
        )

        /**
         * Knob defaults reproduce `LocationProcessor` + `AbnormalDetectionConfig.DEFAULT` +
         * `LocationTrackingConstants` exactly — this profile with [DeviceEnvelope.Default] is
         * numerically identical to the algorithm shipping in Mileway today.
         */
        public fun milewayV1Profile(): TuningProfile =
            TuningProfile(algorithmId = AlgorithmId.TieredGps, profileId = "mileway.v1")

        private fun scaledSec(baseSec: Double, multiplier: Double): Long =
            (baseSec * multiplier).toLong().coerceAtLeast(1L)

        private fun Double.inRange(min: Double, max: Double): Boolean = this in min..max
    }
}
