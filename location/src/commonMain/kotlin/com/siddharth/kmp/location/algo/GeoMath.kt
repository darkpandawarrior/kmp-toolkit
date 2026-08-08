package com.siddharth.kmp.location.algo

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle (haversine) distance in metres.
 *
 * Deliberately byte-for-byte the same formula, the same earth radius and the same order of
 * operations as the consuming app's own `haversineMeters`. That is not copy-paste laziness — an
 * algorithm extracted into this module has to produce *bit-identical* distances to the
 * implementation it replaces, or the regression test that guards the extraction fails for a
 * reason that has nothing to do with the algorithm. Floating-point addition is not associative,
 * so "an equivalent formula" is not good enough here.
 *
 * If this ever needs to change, change it in both places in the same commit, and expect the
 * accuracy fixtures to need re-baselining.
 */
public fun haversineMeters(
    lat1: Double,
    lng1: Double,
    lat2: Double,
    lng2: Double,
): Double {
    val earthRadiusM = 6_371_000.0
    val dLat = (lat2 - lat1) * PI / 180.0
    val dLng = (lng2 - lng1) * PI / 180.0
    val a =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
            sin(dLng / 2) * sin(dLng / 2)
    return earthRadiusM * (2 * atan2(sqrt(a), sqrt(1 - a)))
}
