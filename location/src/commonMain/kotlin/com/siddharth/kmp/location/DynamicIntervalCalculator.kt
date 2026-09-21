package com.siddharth.kmp.location

import kotlin.math.roundToLong

/** Inputs for one location-request interval recomputation. */
data class IntervalInputs(
    val speedMps: Double,
    val batteryPct: Int,
    val isCharging: Boolean,
    val isPowerSaver: Boolean,
    val elapsedMs: Long,
    // Wave-2 DeviceTierManager: RAM-tier interval multiplier. Default 1.0 (HIGH tier / no change)
    // preserves every existing call site's behavior.
    val tierMultiplier: Double = 1.0,
    // Wave-2 IMU polish: harsh accel/braking this fix — boosts GPS frequency (shorter interval) to
    // capture the event at higher resolution. Default false ⇒ 1.0 ⇒ no behavior change.
    val harshAccel: Boolean = false,
    // P10.1: user-set minimum-interval floor (Track Miles "location interval" setting), in ms.
    // Raises the lower clamp bound so the cadence never goes below the user's chosen floor. Default
    // 0 ⇒ the clamp stays at MIN_INTERVAL_MS ⇒ no behavior change.
    val userFloorMs: Long = 0L,
)

/**
 * Pure, platform-independent location-request interval calculator.
 *
 * Picks a base interval from the current speed band, then *stretches* it for low battery,
 * power-saver mode, and long-running sessions to conserve power. The result is clamped to
 * [[MIN_INTERVAL_MS], [MAX_INTERVAL_MS]].
 *
 * Faster movement → shorter interval (denser track). Low battery / power-saver / long
 * duration → longer interval (fewer fixes). Charging disables the battery penalty entirely.
 *
 * No Android dependency, so it is fully covered by JVM unit tests. The service (C.2a) calls
 * [intervalMs] per fix and re-registers the FLP request only when the value changes ≥1s.
 */
object DynamicIntervalCalculator {
    const val MIN_INTERVAL_MS = 5_000L
    const val MAX_INTERVAL_MS = 60_000L

    // Speed-band upper bounds (m/s).
    private const val IDLE_MAX_MPS = 0.5 // < ~1.8 km/h: stationary
    private const val WALK_MAX_MPS = 4.0 // < ~14 km/h: walking / jogging
    private const val CITY_MAX_MPS = 11.0 // < ~40 km/h: city driving
    private const val HIGHWAY_MAX_MPS = 25.0 // < ~90 km/h: fast road

    // Per-band base intervals (ms): idle 20s → highway 5s.
    private const val BASE_IDLE_MS = 20_000L
    private const val BASE_WALK_MS = 15_000L
    private const val BASE_CITY_MS = 10_000L
    private const val BASE_HIGHWAY_MS = 6_000L
    private const val BASE_FAST_MS = 5_000L

    private fun baseForSpeed(speedMps: Double): Long =
        when {
            speedMps < IDLE_MAX_MPS -> BASE_IDLE_MS
            speedMps < WALK_MAX_MPS -> BASE_WALK_MS
            speedMps < CITY_MAX_MPS -> BASE_CITY_MS
            speedMps < HIGHWAY_MAX_MPS -> BASE_HIGHWAY_MS
            else -> BASE_FAST_MS
        }

    // Battery stretch. Below 30% the cadence halves; below 50% it eases off. Charging disables the
    // penalty entirely, which is why `charging` is the first arm.
    private const val BATTERY_CRITICAL_PCT = 30
    private const val BATTERY_LOW_PCT = 50
    private const val BATTERY_CRITICAL_MULTIPLIER = 2.0
    private const val BATTERY_LOW_MULTIPLIER = 1.5
    private const val NO_STRETCH = 1.0

    private fun batteryMultiplier(
        pct: Int,
        charging: Boolean,
    ): Double =
        when {
            charging -> NO_STRETCH
            pct < BATTERY_CRITICAL_PCT -> BATTERY_CRITICAL_MULTIPLIER
            pct < BATTERY_LOW_PCT -> BATTERY_LOW_MULTIPLIER
            else -> NO_STRETCH
        }

    // Wave-2 IMU polish: shortens (boosts) the interval during a harsh accel/brake event so the
    // GPS track has denser resolution right where the interesting driving behavior happened.
    private const val HARSH_ACCEL_MULTIPLIER = 0.5

    private fun accelMultiplier(harshAccel: Boolean): Double = if (harshAccel) HARSH_ACCEL_MULTIPLIER else NO_STRETCH

    // Session-duration stretch. A long drive is a long drain, so cadence eases as the session runs.
    private const val MILLIS_PER_HOUR = 3_600_000.0
    private const val LONG_SESSION_HOURS = 6
    private const val MEDIUM_SESSION_HOURS = 4
    private const val SHORT_SESSION_HOURS = 2
    private const val LONG_SESSION_MULTIPLIER = 1.75
    private const val MEDIUM_SESSION_MULTIPLIER = 1.5
    private const val SHORT_SESSION_MULTIPLIER = 1.25

    /** Power-saver mode is the user asking for battery over fidelity; honour it with the same easing. */
    private const val POWER_SAVER_MULTIPLIER = 1.5

    private fun durationMultiplier(elapsedMs: Long): Double {
        val hours = elapsedMs / MILLIS_PER_HOUR
        return when {
            hours > LONG_SESSION_HOURS -> LONG_SESSION_MULTIPLIER
            hours > MEDIUM_SESSION_HOURS -> MEDIUM_SESSION_MULTIPLIER
            hours > SHORT_SESSION_HOURS -> SHORT_SESSION_MULTIPLIER
            else -> NO_STRETCH
        }
    }

    /** Compute the location-request interval in milliseconds, clamped to the allowed range. */
    fun intervalMs(inputs: IntervalInputs): Long {
        var ms = baseForSpeed(inputs.speedMps).toDouble()
        ms *= batteryMultiplier(inputs.batteryPct, inputs.isCharging)
        if (inputs.isPowerSaver) ms *= POWER_SAVER_MULTIPLIER
        ms *= durationMultiplier(inputs.elapsedMs)
        ms *= inputs.tierMultiplier
        ms *= accelMultiplier(inputs.harshAccel)
        return ms.roundToLong().coerceIn(maxOf(MIN_INTERVAL_MS, inputs.userFloorMs), MAX_INTERVAL_MS)
    }
}
