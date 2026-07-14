package com.siddharth.kmp.location

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KalmanSmootherTest {
    @Test
    fun firstFixIsPassedThroughUnchanged() {
        val (lat, lng) = KalmanSmoother().smooth(12.9716, 77.5946, accuracyMeters = 10f, timestampMs = 0L)
        assertEquals(12.9716, lat, 1e-9)
        assertEquals(77.5946, lng, 1e-9)
    }

    @Test
    fun noisyFixesAroundAPointConvergeNearIt() {
        val k = KalmanSmoother()
        val trueLat = 12.9716
        val trueLng = 77.5946
        // Alternating noisy measurements ±~11m; the smoothed estimate should sit close to the truth.
        val noise = 1e-4
        var t = 0L
        var last = trueLat to trueLng
        repeat(40) { i ->
            val jitter = if (i % 2 == 0) noise else -noise
            last = k.smooth(trueLat + jitter, trueLng + jitter, accuracyMeters = 12f, timestampMs = t)
            t += 1000L
        }
        assertTrue(abs(last.first - trueLat) < noise, "lat ${last.first} not converged near $trueLat")
        assertTrue(abs(last.second - trueLng) < noise, "lng ${last.second} not converged near $trueLng")
    }

    @Test
    fun resetClearsState() {
        val k = KalmanSmoother()
        k.smooth(1.0, 2.0, 5f, 0L)
        k.reset()
        val (lat, lng) = k.smooth(50.0, 60.0, 5f, 1000L)
        assertEquals(50.0, lat, 1e-9)
        assertEquals(60.0, lng, 1e-9)
    }
}
