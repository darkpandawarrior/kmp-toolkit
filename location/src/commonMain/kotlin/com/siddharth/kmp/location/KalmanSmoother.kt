package com.siddharth.kmp.location

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * Metres per degree of latitude. One degree of latitude is a fixed arc on the WGS-84 ellipsoid,
 * unlike a degree of longitude, which shrinks with cos(latitude) — that is why only this one is a
 * constant and the longitude conversion scales it.
 */
private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

/** Degrees in half a turn — the radians conversion for [cos]. */
private const val DEGREES_PER_HALF_TURN = 180.0

private const val MILLIS_PER_SECOND = 1000.0

/**
 * Lightweight Kalman-like GPS smoother, operates on lat/lng separately, uses time delta
 * to increase process uncertainty.  Pure Kotlin, no Android dependencies.
 */
class KalmanSmoother(
    private var processNoiseMetersPerSec: Double = 1.0,
) {
    private var initialized = false
    private var lat: Double = 0.0
    private var lng: Double = 0.0
    private var pLat: Double = 1.0
    private var pLng: Double = 1.0
    private var lastTimestampMs: Long = 0L

    fun reset() {
        initialized = false
        lat = 0.0
        lng = 0.0
        pLat = 1.0
        pLng = 1.0
        lastTimestampMs = 0L
    }

    fun setProcessNoiseMetersPerSec(mps: Double) {
        processNoiseMetersPerSec = mps.coerceAtLeast(0.0)
    }

    /**
     * Smooth a new GPS measurement.
     * @return Pair(smoothedLat, smoothedLng)
     */
    fun smooth(
        measuredLat: Double,
        measuredLng: Double,
        accuracyMeters: Float,
        timestampMs: Long,
    ): Pair<Double, Double> {
        val measNoiseLat = metersToLatDeg(accuracyMeters.toDouble())
        val measNoiseLng = metersToLngDeg(accuracyMeters.toDouble(), measuredLat)

        if (!initialized) {
            lat = measuredLat
            lng = measuredLng
            pLat = measNoiseLat * measNoiseLat
            pLng = measNoiseLng * measNoiseLng
            lastTimestampMs = timestampMs
            initialized = true
            return Pair(lat, lng)
        }

        val dtSec = max(0.0, (timestampMs - lastTimestampMs).coerceAtLeast(0L) / MILLIS_PER_SECOND)
        val processStd = processNoiseMetersPerSec * dtSec
        val qLat = metersToLatDeg(processStd)
        val qLng = metersToLngDeg(processStd, lat)

        pLat += qLat * qLat
        pLng += qLng * qLng

        val rLat = measNoiseLat * measNoiseLat
        val rLng = measNoiseLng * measNoiseLng
        val kLat = if (pLat + rLat > 0) pLat / (pLat + rLat) else 0.0
        val kLng = if (pLng + rLng > 0) pLng / (pLng + rLng) else 0.0

        lat += kLat * (measuredLat - lat)
        lng += kLng * (measuredLng - lng)
        pLat = (1 - kLat) * pLat
        pLng = (1 - kLng) * pLng
        lastTimestampMs = timestampMs
        return Pair(lat, lng)
    }

    private fun metersToLatDeg(meters: Double): Double = meters / METERS_PER_DEGREE_LATITUDE

    private fun metersToLngDeg(
        meters: Double,
        atLatDeg: Double,
    ): Double {
        val metersPerDeg = METERS_PER_DEGREE_LATITUDE * cos(atLatDeg * PI / DEGREES_PER_HALF_TURN)
        return if (metersPerDeg <= 0.0) meters / METERS_PER_DEGREE_LATITUDE else meters / metersPerDeg
    }
}
