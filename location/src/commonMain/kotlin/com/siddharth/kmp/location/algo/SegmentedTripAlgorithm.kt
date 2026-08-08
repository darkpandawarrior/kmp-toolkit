package com.siddharth.kmp.location.algo

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A decorator that adds the three corrections [TieredGpsAlgorithm] structurally cannot make,
 * without touching it.
 *
 * The delegate is a good per-fix classifier, but it decides each fix in isolation and against fixed
 * thresholds. That leaves three one-sided errors on the table:
 *
 * 1. **Accuracy-blind jitter gate.** A 6 m step reported with 30 m accuracy is inside its own error
 *    ellipse — it is noise, not travel. A fixed 5 m gate passes it and banks it. This over-counts,
 *    always in the same direction, and worst exactly where accuracy is worst (urban canyon, tunnels
 *    mouths, dense city). A bias does not average out over a year of claims; noise does.
 * 2. **Trusting the wrong speed.** Speed differenced from two positions divides position error by a
 *    small dt, which manufactures 40 m/s "speeds" from a parked car and defeats every speed-banded
 *    gate downstream. Doppler speed from the GNSS chipset is independent of the position error being
 *    filtered, so it is the better input when it is trustworthy.
 * 3. **No notion of a stop.** Twenty minutes in a car park is a slow random walk that accumulates
 *    real metres one legitimate-looking step at a time. No per-fix rule can see it, because no
 *    single fix is wrong — only the sequence is.
 *
 * **How a window fits through a one-fix-at-a-time interface.** The trick is to buffer the
 * *attribution*, never the fixes. Every fix is still emitted and persisted the moment it arrives —
 * the map draws, the route is complete, nothing is hidden from the user. Only the decision about
 * whether those metres *count* is deferred until the stop is confirmed or refuted. That keeps
 * [process] honest (one fix in, one result out, never retracted) while still allowing a decision
 * that genuinely needs to see the future.
 *
 * Set `enableStopSuppression = false` and `jitterAccuracyFactor = 0.0` and this becomes bit-identical
 * to the delegate. That is not a courtesy — it is how the parity test is written, and it is what
 * makes "did my change help?" answerable instead of arguable.
 *
 * ponytail: a decorator, not a fork. Re-implementing the delegate's Kalman/band/gap/spike logic to
 * add three rules would double the surface that has to stay correct, and the two copies would drift.
 */
public class SegmentedTripAlgorithm(
    private val profile: TuningProfile = defaultProfile(),
    private val envelope: DeviceEnvelope = DeviceEnvelope.Default,
    private val delegate: MileageAlgorithm = TieredGpsAlgorithm(
        TuningProfile(AlgorithmId.TieredGps, "mileway.v1"),
        envelope,
    ),
) : MileageAlgorithm {

    override val id: AlgorithmId = Id
    override val knobs: List<KnobSpec> = ALL_KNOBS + delegate.knobs

    // — Step 0: speed source —
    private val preferDoppler = profile.b(FLAG_PREFER_DOPPLER, default = true)
    private val dopplerMaxAccuracyM = profile.d(DopplerMaxAccuracy)
    private val derivedSpeedMinDtSec = profile.d(DerivedSpeedMinDt)
    private val speedDisagreementRatio = profile.d(SpeedDisagreementRatio)

    // — Step 2: accuracy-scaled jitter gate —
    private val jitterAccuracyFactor = profile.d(JitterAccuracyFactor)

    // — Step 3: stop detection —
    private val stopEnabled = profile.b(FLAG_ENABLE_STOP_SUPPRESSION, default = true)
    private val stopSpeedMps = profile.d(StopSpeed)
    private val stopRadiusM = profile.d(StopRadius)
    private val stopExitRadiusM = profile.d(StopExitRadius)
    private val stopConfirmSec = profile.d(StopConfirmSec)

    private var lastAccepted: Fix? = null
    private var stopCentroidLat = 0.0
    private var stopCentroidLng = 0.0
    private var stopSinceMs = 0L
    private var inCandidateStop = false
    private var pendingStopDistanceM = 0.0
    private var pendingLegs = 0

    private var stopsConfirmed = 0.0
    private var speedDisagreements = 0.0
    private var accuracyGated = 0.0

    /**
     * Own accounting, deliberately NOT the delegate's.
     *
     * The delegate banks a leg the instant it classifies one, and there is no API to unbank it. This
     * class exists precisely to overrule some of those decisions, so proxying the delegate's totals
     * would report the numbers it wanted rather than the ones actually returned to the caller — the
     * suppressed metres would vanish from every `FixResult` and still show up in the total. So the
     * accumulators here are driven only by the results this class actually emits.
     */
    private var own = AlgorithmState()

    override fun reset(session: SessionContext) {
        delegate.reset(session)
        lastAccepted = null
        clearCandidateStop()
        stopsConfirmed = 0.0
        speedDisagreements = 0.0
        accuracyGated = 0.0
        own = AlgorithmState()
    }

    /** Fold one emitted result into the running totals. Exactly one bucket per leg. */
    private fun bank(r: FixResult, fix: Fix?): FixResult {
        val d = r.distanceDeltaM
        val counted = r.bucket != DistanceBucket.NONE
        val n = if (counted) own.accepted + 1 else own.accepted
        val speed = fix?.speedMps ?: 0.0
        own = own.copy(
            // A spike was never travelled, so it is excluded from `originalM` by design — that is
            // what makes the invariant `cleaned == original - abnormal - mock` hold.
            originalM = if (counted && r.bucket != DistanceBucket.SPIKE) own.originalM + d else own.originalM,
            cleanedM = if (r.bucket == DistanceBucket.CLEANED) own.cleanedM + d else own.cleanedM,
            abnormalM = if (r.bucket == DistanceBucket.ABNORMAL) own.abnormalM + d else own.abnormalM,
            mockM = if (r.bucket == DistanceBucket.MOCK) own.mockM + d else own.mockM,
            spikeM = if (r.bucket == DistanceBucket.SPIKE) own.spikeM + d else own.spikeM,
            accepted = n,
            rejected = if (r.emitted == null) own.rejected + 1 else own.rejected,
            consecutiveNormal = if (r.bucket == DistanceBucket.CLEANED) own.consecutiveNormal + 1 else 0,
            maxSpeedMps = max(own.maxSpeedMps, speed),
            avgSpeedMps = if (n > 0) ((own.avgSpeedMps * (n - 1)) + speed) / n else own.avgSpeedMps,
            lastFix = fix ?: own.lastFix,
        )
        return r
    }

    override fun process(fix: Fix): FixResult {
        val prev = lastAccepted

        // ── Step 2 addition: reject motion that is smaller than the fix's own uncertainty. ────────
        // Done BEFORE delegating, because once the delegate banks a leg there is no way to unbank it.
        if (prev != null && jitterAccuracyFactor > 0.0) {
            val d = haversineMeters(prev.lat, prev.lng, fix.lat, fix.lng)
            if (d < fix.accuracyM * jitterAccuracyFactor) {
                accuracyGated += 1.0
                return bank(
                    FixResult(
                        verdict = FixVerdict.JITTER,
                        emitted = fix,
                        displacementM = d,
                        distanceDeltaM = 0.0,
                        bucket = DistanceBucket.NONE,
                        reason = "inside error ellipse: ${fmt1(d)}m < ${fmt1(fix.accuracyM)}m x $jitterAccuracyFactor",
                    ),
                    fix,
                )
            }
        }

        val result = delegate.process(fix)

        // Only a fix the delegate actually counted can start, sustain or refute a stop. A rejected
        // or spiked fix says nothing about whether the vehicle is parked.
        if (!stopEnabled || result.emitted == null) {
            if (result.bucket == DistanceBucket.CLEANED) lastAccepted = fix
            return bank(result, fix)
        }

        val speed = speedFor(fix, prev)
        val accurateEnoughToJudgeStop = fix.accuracyM <= envelope.stationaryAccuracyCeilingM
        lastAccepted = fix

        if (!inCandidateStop) {
            val looksStopped = speed < stopSpeedMps && accurateEnoughToJudgeStop
            if (looksStopped) beginCandidateStop(fix)
            return bank(result, fix)
        }

        // ── Inside a candidate stop ───────────────────────────────────────────────────────────────
        val fromCentroid = haversineMeters(stopCentroidLat, stopCentroidLng, fix.lat, fix.lng)

        // Refuted: the vehicle was crawling, not parked. Every held metre is real after all.
        if (fromCentroid > stopExitRadiusM || speed >= stopSpeedMps) {
            val drained = pendingStopDistanceM
            val legs = pendingLegs
            clearCandidateStop()
            return if (drained > 0.0) {
                bank(
                    result.copy(
                        distanceDeltaM = result.distanceDeltaM + drained,
                        bucket = DistanceBucket.CLEANED,
                        reason = "stop refuted: drained $legs held leg(s), ${fmt1(drained)}m",
                    ),
                    fix,
                )
            } else {
                bank(result, fix)
            }
        }

        // Confirmed: long enough inside the radius that this is parking, not traffic. The held
        // metres were drift around a stationary vehicle and are discarded permanently.
        val heldSec = (fix.timeMs - stopSinceMs) / 1000.0
        if (heldSec >= stopConfirmSec) {
            stopsConfirmed += 1.0
            pendingStopDistanceM = 0.0
            pendingLegs = 0
        } else {
            pendingStopDistanceM += result.distanceDeltaM
            pendingLegs += 1
        }

        return bank(
            result.copy(
                verdict = FixVerdict.PAUSED,
                distanceDeltaM = 0.0,
                bucket = DistanceBucket.NONE,
                reason = "candidate stop ${fmt1(heldSec)}s, holding ${fmt1(pendingStopDistanceM)}m",
            ),
            fix,
        )
    }

    /**
     * The trip ended while a stop was still unresolved.
     *
     * Discarding is the right call: a trip that ends where the vehicle stopped moving ended *at* the
     * stop, so the drift inside it was never travel. Without this the held metres would sit in limbo
     * and quietly vanish from the total with no record of why.
     */
    override fun flush(): List<FixResult> {
        if (!inCandidateStop) return emptyList()
        val discarded = pendingStopDistanceM
        val legs = pendingLegs
        val at = lastAccepted
        clearCandidateStop()
        stopsConfirmed += 1.0
        return listOf(
            FixResult(
                verdict = FixVerdict.PAUSED,
                emitted = null,
                displacementM = 0.0,
                distanceDeltaM = 0.0,
                bucket = DistanceBucket.NONE,
                reason = "trip ended inside a stop: discarded $legs leg(s), ${fmt1(discarded)}m" +
                    (at?.let { " at ${it.lat},${it.lng}" } ?: ""),
            ),
        )
    }

    override fun snapshot(): AlgorithmState {
        // `own`, not the delegate's — see the `own` field comment. The delegate's opaque map is
        // still carried through so its private continuation state (Kalman covariance and the like)
        // survives a resume.
        return own.copy(
            opaque = delegate.snapshot().opaque +
                mapOf(
                    OPAQUE_STOPS_CONFIRMED to stopsConfirmed,
                    OPAQUE_SPEED_DISAGREEMENTS to speedDisagreements,
                    OPAQUE_ACCURACY_GATED to accuracyGated,
                    // Held metres are part of the resumable state: a process death mid-stop must not
                    // silently convert "undecided" into "counted".
                    OPAQUE_PENDING_STOP_M to pendingStopDistanceM,
                ),
        )
    }

    override fun restore(state: AlgorithmState) {
        delegate.restore(state)
        own = state
        stopsConfirmed = state.opaque[OPAQUE_STOPS_CONFIRMED] ?: 0.0
        speedDisagreements = state.opaque[OPAQUE_SPEED_DISAGREEMENTS] ?: 0.0
        accuracyGated = state.opaque[OPAQUE_ACCURACY_GATED] ?: 0.0
        pendingStopDistanceM = state.opaque[OPAQUE_PENDING_STOP_M] ?: 0.0
        pendingLegs = if (pendingStopDistanceM > 0.0) 1 else 0
        // Same rule the interface documents: the anchor is dropped so the untracked gap is never
        // bridged. The candidate stop is also dropped — we cannot know whether the vehicle stayed
        // put while we were dead, so we re-observe rather than assume.
        lastAccepted = null
        inCandidateStop = false
    }

    // ── internals ─────────────────────────────────────────────────────────────────────────────────

    private fun beginCandidateStop(fix: Fix) {
        inCandidateStop = true
        stopCentroidLat = fix.lat
        stopCentroidLng = fix.lng
        stopSinceMs = fix.timeMs
        pendingStopDistanceM = 0.0
        pendingLegs = 0
    }

    private fun clearCandidateStop() {
        inCandidateStop = false
        pendingStopDistanceM = 0.0
        pendingLegs = 0
        stopSinceMs = 0L
    }

    /**
     * Pick the speed to trust.
     *
     * Doppler wins when the chipset looks healthy. Position-differenced speed is only computed above
     * a dt floor, because below it the division amplifies position noise into implausible speeds —
     * a real bug class, not a hypothetical. When the two disagree wildly, take the lower: on a
     * reimbursement claim, the conservative number is the defensible one.
     */
    internal fun speedFor(fix: Fix, prev: Fix?): Double {
        val doppler = fix.speedMps?.takeIf { preferDoppler && it >= 0.0 && fix.accuracyM <= dopplerMaxAccuracyM }
        val dtSec = if (prev == null) 0.0 else (fix.timeMs - prev.timeMs) / 1000.0
        val derived = if (prev != null && dtSec >= derivedSpeedMinDtSec) {
            haversineMeters(prev.lat, prev.lng, fix.lat, fix.lng) / dtSec
        } else {
            null
        }

        return when {
            doppler != null && derived != null -> {
                val hi = max(doppler, derived)
                val lo = min(doppler, derived)
                val disagree = lo > 0.0 && hi / lo > speedDisagreementRatio
                if (disagree) speedDisagreements += 1.0
                if (disagree) lo else doppler
            }
            doppler != null -> doppler
            derived != null -> derived
            else -> 0.0
        }
    }

    public companion object {
        public val Id: AlgorithmId = AlgorithmId("segmented-trip")

        public const val FLAG_PREFER_DOPPLER: String = "preferDopplerSpeed"
        public const val FLAG_ENABLE_STOP_SUPPRESSION: String = "enableStopSuppression"

        public const val OPAQUE_STOPS_CONFIRMED: String = "stopsConfirmed"
        public const val OPAQUE_SPEED_DISAGREEMENTS: String = "speedDisagreements"
        public const val OPAQUE_ACCURACY_GATED: String = "accuracyGated"
        public const val OPAQUE_PENDING_STOP_M: String = "pendingStopM"

        public val DopplerMaxAccuracy: KnobSpec = KnobSpec(
            "dopplerMaxAccuracyM", 30.0, 5.0, 100.0, 5.0, "m",
            "Above this reported accuracy, the chipset's Doppler speed is not trusted either.",
        )
        public val DerivedSpeedMinDt: KnobSpec = KnobSpec(
            "derivedSpeedMinDtSec", 1.0, 0.2, 10.0, 0.2, "s",
            "Below this gap, position-differenced speed divides noise by a tiny dt. Do not compute it.",
        )
        public val SpeedDisagreementRatio: KnobSpec = KnobSpec(
            "speedDisagreementRatio", 3.0, 1.5, 10.0, 0.5, "x",
            "When Doppler and derived speed differ by more than this, take the lower one.",
        )
        public val JitterAccuracyFactor: KnobSpec = KnobSpec(
            "jitterAccuracyFactor", 0.5, 0.0, 3.0, 0.1, "x",
            "Reject displacement below accuracy x this. 0.0 disables, giving exact delegate parity.",
        )
        public val StopSpeed: KnobSpec = KnobSpec(
            "stopSpeedMps", 0.8, 0.0, 5.0, 0.1, "m/s", "Below this speed a stop becomes a candidate.",
        )
        public val StopRadius: KnobSpec = KnobSpec(
            "stopRadiusM", 25.0, 5.0, 200.0, 5.0, "m", "Candidate-stop centroid radius.",
        )
        public val StopExitRadius: KnobSpec = KnobSpec(
            "stopExitRadiusM", 40.0, 10.0, 300.0, 5.0, "m", "Leaving this radius refutes the stop.",
        )
        public val StopConfirmSec: KnobSpec = KnobSpec(
            // Deliberately far above a traffic-light cycle. Stop suppression is the one rule here
            // that can UNDER-count, and stop-and-go city driving is the market's documented weak
            // spot, so this errs heavily towards counting.
            "stopConfirmSec", 120.0, 15.0, 900.0, 15.0, "s",
            "Time inside the radius before a stop is confirmed and its drift discarded.",
        )

        public val ALL_KNOBS: List<KnobSpec> = listOf(
            DopplerMaxAccuracy, DerivedSpeedMinDt, SpeedDisagreementRatio,
            JitterAccuracyFactor, StopSpeed, StopRadius, StopExitRadius, StopConfirmSec,
        )

        public fun defaultProfile(): TuningProfile = TuningProfile(Id, "segmented.v1")

        /** Every addition disabled — provably identical to the bare delegate. */
        public fun parityProfile(): TuningProfile = TuningProfile(Id, "parity.tiered-gps")
            .with(JitterAccuracyFactor.name, 0.0)
            .with(FLAG_ENABLE_STOP_SUPPRESSION, false)

        private fun fmt1(v: Double): String {
            val r = kotlin.math.round(v * 10.0) / 10.0
            return if (abs(r) >= 1e6) r.toString() else r.toString()
        }
    }
}
