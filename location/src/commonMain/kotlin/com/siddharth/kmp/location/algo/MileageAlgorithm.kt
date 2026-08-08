package com.siddharth.kmp.location.algo

import kotlin.jvm.JvmInline

/**
 * The seam that lets more than one mileage algorithm exist at the same time.
 *
 * Today a tracking app has exactly one distance algorithm, welded into the service that acquires
 * the fixes. That makes three ordinary questions unanswerable: *is the other algorithm better on
 * this route*, *what does this threshold actually buy*, and *did the change I just made help*.
 * You cannot answer any of them by reading code, because the algorithm cannot be run without a
 * phone, a drive, and a day.
 *
 * This interface exists to make those questions cheap. Everything here is deliberately pure:
 *
 *  - no coroutines, no `Dispatchers`, no I/O, no logging
 *  - **no clock** — time arrives as [Fix.timeMs] and never as `now()`
 *  - no platform types, no Android, no dependencies at all
 *
 * That purity is not tidiness for its own sake. It is the single property that makes replay,
 * shadow-mode comparison and parameter sweeps possible: an algorithm that cannot be re-run
 * deterministically over a recorded trace cannot be compared against anything, and a comparison
 * you cannot re-run is an opinion.
 *
 * `:location` is the right home precisely because it has zero dependencies and builds for every
 * target including `wasmJs`. Keep it that way — the moment this file needs a dependency to
 * compile, the harness that replays traces in a browser preview stops working.
 *
 * ponytail: three implementations, not four. Two of the four known algorithms differ only in
 * threshold constants, so they are two [TuningProfile]s of one class, not two classes. Sibling
 * classes that differ only in numbers are the mistake this abstraction exists to avoid.
 */
public interface MileageAlgorithm {

    /** Stable identity, used for persistence, selection and shadow-mode result attribution. */
    public val id: AlgorithmId

    /** The knobs this algorithm understands. Drives generic tuning UI and automated sweeps. */
    public val knobs: List<KnobSpec>

    /**
     * Begin a new journey. Must clear all accumulated state.
     *
     * A resumed journey calls [restore] *after* this, so implementations should not try to be
     * clever about [SessionContext.resumedFromPause] here beyond seeding session-scoped values.
     */
    public fun reset(session: SessionContext)

    /**
     * Consume one fix and report what it contributed.
     *
     * Must be pure with respect to its arguments and this instance's own state. Calling
     * `process` twice with the same fix on two identically-restored instances must produce
     * identical results — that is what makes shadow mode trustworthy.
     */
    public fun process(fix: Fix): FixResult

    /**
     * Emit anything still buffered and end the journey.
     *
     * An algorithm that looks at a window of fixes — a median filter, a stop detector, a
     * map-matcher — has pending output when the last fix arrives, and no way to know it was the
     * last. Without this, the tail of every trip is silently dropped. Online algorithms that
     * buffer nothing correctly return an empty list, which is why this has a default.
     *
     * Added after a cross-model design review flagged the missing terminator as the most likely
     * source of a quiet, systematic under-count.
     */
    public fun flush(): List<FixResult> = emptyList()

    /** Capture enough state to resume this journey later. Must round-trip with [restore]. */
    public fun snapshot(): AlgorithmState

    /**
     * Resume from a [snapshot].
     *
     * Implementations must leave "last fix" unset for distance purposes even when the snapshot
     * carries one, so that ground covered while tracking was stopped is never counted. The
     * accumulated totals resume; the bridge across the gap does not.
     */
    public fun restore(state: AlgorithmState)
}

/** Stable algorithm identity. A string so that persisted rows survive refactors of the enum-ish set. */
@JvmInline
public value class AlgorithmId(public val value: String) {
    override fun toString(): String = value

    public companion object {
        public val TieredGps: AlgorithmId = AlgorithmId("tiered-gps")
        public val PassThrough: AlgorithmId = AlgorithmId("pass-through")
        public val OdometerPrimary: AlgorithmId = AlgorithmId("odometer-primary")
    }
}

/**
 * One location sample, stripped to what a distance algorithm can legitimately use.
 *
 * Deliberately not the platform's location type: binding the algorithm to `android.location.Location`
 * or `CLLocation` is what made the original implementations untestable off-device.
 */
public data class Fix(
    val lat: Double,
    val lng: Double,
    /** Epoch millis **of the fix**, not of its arrival. Never substitute a wall clock here. */
    val timeMs: Long,
    val accuracyM: Double,
    val speedMps: Double? = null,
    val bearingDeg: Double? = null,
    val altitudeM: Double? = null,
    val isMock: Boolean = false,
    val provider: String? = null,
    /**
     * Vehicle odometer reading in metres, when one is available.
     *
     * Nullable and last because most sources never have it. It exists because an
     * odometer-primary algorithm — where the odometer is the distance of record and GPS merely
     * corroborates — is otherwise impossible to express through this seam at all: you cannot
     * derive an odometer reading from a stream of GPS fixes. Caught by a cross-model design
     * review; adding it now costs nothing, adding it later would change every call site.
     */
    val odometerM: Double? = null,
)

/** What happened to a fix. Diagnostic granularity — never branch business logic on this. */
public enum class FixVerdict {
    ACCEPTED,
    REJECTED_BOUNDS,
    REJECTED_ACCURACY,
    JITTER,
    DUPLICATE,
    ABNORMAL,
    SPIKE,
    GAP_RECOVERED,
    PAUSED,
}

/**
 * Which running total a fix's displacement was added to.
 *
 * The buckets are the point of the whole design: a user is shown a *cleaned* distance they can
 * trust, while the raw figure stays recoverable for audit. `NONE` means the displacement was
 * measured but deliberately counted nowhere.
 */
public enum class DistanceBucket { CLEANED, ABNORMAL, MOCK, SPIKE, NONE }

public data class FixResult(
    val verdict: FixVerdict,
    /**
     * The fix as it should be persisted — possibly coordinate-modified, e.g. by smoothing.
     * `null` means "do not persist this fix at all".
     */
    val emitted: Fix?,
    /** Straight-line metres from the previous accepted fix. Reported even when counted nowhere. */
    val displacementM: Double,
    /** Metres added to [bucket]. Zero whenever [bucket] is [DistanceBucket.NONE]. */
    val distanceDeltaM: Double,
    val bucket: DistanceBucket,
    /** Human-readable why, for debug surfaces and trace reports only. */
    val reason: String? = null,
)

/**
 * Everything needed to resume a journey across process death.
 *
 * [opaque] is an escape hatch for implementation-private continuation state (a Kalman covariance,
 * a rolling speed window's summary) so that adding such state to one algorithm does not force a
 * schema change on every other one.
 */
public data class AlgorithmState(
    /**
     * Schema version of this snapshot.
     *
     * Snapshots are written to a device database and read back after an app update, so a field
     * added here months from now will meet rows written by the old code. Without a version the
     * only options at that point are "guess" or "crash on launch". Bump it whenever the meaning
     * or set of fields changes, and refuse (don't reinterpret) a version you do not understand.
     */
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val cleanedM: Double = 0.0,
    val abnormalM: Double = 0.0,
    val mockM: Double = 0.0,
    val spikeM: Double = 0.0,
    val originalM: Double = 0.0,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val consecutiveNormal: Int = 0,
    val maxSpeedMps: Double = 0.0,
    val avgSpeedMps: Double = 0.0,
    val lastFix: Fix? = null,
    val opaque: Map<String, Double> = emptyMap(),
) {
    /**
     * The invariant every algorithm must preserve: cleaned distance is the original minus the
     * parts we deliberately refused to trust. Spike distance is excluded from `original` by
     * design — a teleport was never travelled, so it is not "distance we then removed".
     *
     * This only holds because [FixResult.bucket] is a SINGLE value: every leg is attributed to
     * exactly one bucket, never two. A fix that is both mock and implausible is one or the other,
     * not both — otherwise its distance would be subtracted twice and `cleanedM` would come out
     * short. Any future algorithm that wants to attribute one leg to two buckets breaks this
     * invariant and must not be written that way.
     */
    public fun invariantHolds(toleranceM: Double = 0.5): Boolean =
        kotlin.math.abs(cleanedM - (originalM - abnormalM - mockM)) <= toleranceM

    public companion object {
        public const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/** Journey-scoped inputs that are not properties of any single fix. */
public data class SessionContext(
    val startedAtMs: Long,
    val resumedFromPause: Boolean = false,
    val vehicleType: String? = null,
)

/**
 * One tunable parameter, described well enough to build a slider and run a sweep without
 * knowing anything about the algorithm behind it.
 */
public data class KnobSpec(
    val name: String,
    val default: Double,
    val min: Double,
    val max: Double,
    val step: Double,
    /** "m", "m/s", "ms", "x" — display only. */
    val unit: String,
    val description: String = "",
) {
    init {
        require(min <= default && default <= max) { "knob '$name': default $default outside [$min, $max]" }
        require(step > 0.0) { "knob '$name': step must be positive" }
    }

    public fun clamp(value: Double): Double = value.coerceIn(min, max)
}

/**
 * A named set of knob values for one algorithm.
 *
 * This is how "the current app's algorithm" and "the other app's algorithm" stop being two
 * codebases and become two rows of numbers that can be diffed and A/B'd.
 */
public data class TuningProfile(
    val algorithmId: AlgorithmId,
    /** e.g. "mileway.v1", "prod.reference", "sdk.passthrough" */
    val profileId: String,
    val values: Map<String, Double> = emptyMap(),
    val flags: Map<String, Boolean> = emptyMap(),
) {
    /** Read a knob, falling back to its declared default and clamping into its declared range. */
    public fun d(spec: KnobSpec): Double = spec.clamp(values[spec.name] ?: spec.default)

    public fun b(name: String, default: Boolean): Boolean = flags[name] ?: default

    public fun with(name: String, value: Double): TuningProfile =
        copy(values = values + (name to value))

    public fun with(name: String, value: Boolean): TuningProfile =
        copy(flags = flags + (name to value))
}

/**
 * Device-derived scaling, computed once at the platform edge and handed in.
 *
 * A budget phone with a poor GPS chipset needs looser gates than a flagship, but the *algorithm*
 * must not be the thing that knows about `ActivityManager.MemoryInfo`. Multipliers keep the
 * device story on the platform side of the seam where it belongs.
 */
public data class DeviceEnvelope(
    val accuracyCeilingM: Double = 50.0,
    val stationaryAccuracyCeilingM: Double = 25.0,
    val minDisplacementMultiplier: Double = 1.0,
    val abnormalThresholdMultiplier: Double = 1.0,
    val gpsIntervalMultiplier: Double = 1.0,
) {
    public companion object {
        public val Default: DeviceEnvelope = DeviceEnvelope()
    }
}

/**
 * Where algorithms are looked up by id.
 *
 * Deliberately a plain map rather than a service-loader or DI-aware thing: it must work
 * identically in a unit test, in a sweep harness on the desktop, and in the app.
 */
public class MileageAlgorithmRegistry(
    factories: Map<AlgorithmId, (TuningProfile, DeviceEnvelope) -> MileageAlgorithm> = emptyMap(),
) {
    private val factories = factories.toMutableMap()

    public fun register(
        id: AlgorithmId,
        factory: (TuningProfile, DeviceEnvelope) -> MileageAlgorithm,
    ): MileageAlgorithmRegistry = apply { factories[id] = factory }

    public val ids: Set<AlgorithmId> get() = factories.keys

    public fun create(
        profile: TuningProfile,
        envelope: DeviceEnvelope = DeviceEnvelope.Default,
    ): MileageAlgorithm {
        val factory = factories[profile.algorithmId]
            ?: error(
                "No algorithm registered for '${profile.algorithmId}'. " +
                    "Registered: ${factories.keys.joinToString()}",
            )
        return factory(profile, envelope)
    }
}
