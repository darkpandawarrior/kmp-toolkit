package com.siddharth.kmp.location.algo

import kotlin.math.abs

/**
 * Trust the source; filter nothing.
 *
 * This is the posture of an app that delegates motion detection to a background-geolocation SDK:
 * the SDK has already decided what a real movement is, so re-filtering its output would be
 * second-guessing a black box with worse information than it had.
 *
 * It is also the honest **benchmark baseline**. Every claim of the form "our filtering improves
 * accuracy" is meaningless without a number for "what if we did nothing at all" — this class is
 * that number. Keep it even if no product ever ships it.
 *
 * The one thing it does do is drop exact duplicates. Duplicate delivery of the same fix is a
 * transport artefact, not a movement, and counting one twice is a bug under any philosophy.
 */
public class PassThroughAlgorithm(
    private val profile: TuningProfile = defaultProfile(),
) : MileageAlgorithm {

    override val id: AlgorithmId = AlgorithmId.PassThrough

    override val knobs: List<KnobSpec> = ALL_KNOBS

    private val maxAccuracyM = profile.d(MaxAccuracy)
    private val countMockDistance = profile.b(FLAG_COUNT_MOCK, default = false)

    private var last: Fix? = null
    private var seenKeys = HashSet<String>()
    private var state = AlgorithmState()

    override fun reset(session: SessionContext) {
        last = null
        seenKeys = HashSet()
        state = AlgorithmState()
    }

    override fun process(fix: Fix): FixResult {
        // A fix is identified by where and when, not by object identity: the same sample
        // redelivered after a reconnect is the same sample.
        val key = dedupeKey(fix)
        if (!seenKeys.add(key)) {
            state = state.copy(rejected = state.rejected + 1)
            return FixResult(FixVerdict.DUPLICATE, emitted = null, 0.0, 0.0, DistanceBucket.NONE, "duplicate $key")
        }

        if (!fix.lat.isFiniteCoord(90.0) || !fix.lng.isFiniteCoord(180.0)) {
            state = state.copy(rejected = state.rejected + 1)
            return FixResult(FixVerdict.REJECTED_BOUNDS, null, 0.0, 0.0, DistanceBucket.NONE, "out of bounds")
        }

        // Even a pass-through refuses a fix the source itself labelled as garbage-accurate,
        // because such a fix is not a claim about position at all.
        if (maxAccuracyM > 0.0 && fix.accuracyM > maxAccuracyM) {
            state = state.copy(rejected = state.rejected + 1)
            return FixResult(
                FixVerdict.REJECTED_ACCURACY, emitted = fix, 0.0, 0.0, DistanceBucket.NONE,
                "accuracy ${fix.accuracyM}m > ${maxAccuracyM}m",
            )
        }

        val prev = last
        val displacement = if (prev == null) 0.0 else haversineMeters(prev.lat, prev.lng, fix.lat, fix.lng)
        last = fix

        val bucket = when {
            fix.isMock && !countMockDistance -> DistanceBucket.MOCK
            else -> DistanceBucket.CLEANED
        }
        val delta = if (prev == null) 0.0 else displacement

        val speed = fix.speedMps ?: 0.0
        val n = state.accepted + 1
        state = state.copy(
            originalM = state.originalM + delta,
            cleanedM = if (bucket == DistanceBucket.CLEANED) state.cleanedM + delta else state.cleanedM,
            mockM = if (bucket == DistanceBucket.MOCK) state.mockM + delta else state.mockM,
            accepted = n,
            consecutiveNormal = state.consecutiveNormal + 1,
            maxSpeedMps = maxOf(state.maxSpeedMps, speed),
            avgSpeedMps = ((state.avgSpeedMps * (n - 1)) + speed) / n,
            lastFix = fix,
        )

        return FixResult(
            verdict = if (bucket == DistanceBucket.MOCK) FixVerdict.ABNORMAL else FixVerdict.ACCEPTED,
            emitted = fix,
            displacementM = displacement,
            distanceDeltaM = delta,
            bucket = bucket,
        )
    }

    override fun snapshot(): AlgorithmState = state

    override fun restore(state: AlgorithmState) {
        this.state = state
        // Intentionally NOT restoring `last`: the ground covered while tracking was stopped must
        // never be bridged into the total. The first fix after a resume is an anchor, not a leg.
        this.last = null
    }

    public companion object {
        public const val FLAG_COUNT_MOCK: String = "countMockDistance"

        public val MaxAccuracy: KnobSpec = KnobSpec(
            name = "maxAccuracyM",
            default = 0.0, // 0 => accept everything the source emits
            min = 0.0,
            max = 500.0,
            step = 5.0,
            unit = "m",
            description = "Reject fixes worse than this. 0 disables the check entirely.",
        )

        public val ALL_KNOBS: List<KnobSpec> = listOf(MaxAccuracy)

        public fun defaultProfile(): TuningProfile =
            TuningProfile(algorithmId = AlgorithmId.PassThrough, profileId = "sdk.passthrough")

        /** Where + when. Matches the natural UNIQUE index for de-duplicating persisted rows. */
        public fun dedupeKey(fix: Fix): String =
            "${fix.timeMs}:${fix.lat.round6()}:${fix.lng.round6()}"

        private fun Double.round6(): Long = kotlin.math.round(this * 1_000_000.0).toLong()

        private fun Double.isFiniteCoord(limit: Double): Boolean =
            !this.isNaN() && !this.isInfinite() && abs(this) <= limit
    }
}
