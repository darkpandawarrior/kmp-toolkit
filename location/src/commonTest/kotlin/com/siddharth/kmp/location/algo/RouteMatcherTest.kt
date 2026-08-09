package com.siddharth.kmp.location.algo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RouteMatcherTest {

    @Test
    fun matchRequest_from_fixes_carries_accuracy_as_the_radius() {
        val fixes = listOf(
            Fix(lat = 12.9, lng = 77.6, timeMs = 1000L, accuracyM = 8.0),
            Fix(lat = 12.901, lng = 77.6, timeMs = 2000L, accuracyM = 15.0),
        )
        val request = MatchRequest.from(fixes)

        assertEquals(listOf(8.0, 15.0), request.points.map { it.radiusM })
        assertEquals(listOf(1000L, 2000L), request.points.map { it.timeMs })
    }

    @Test
    fun matchRequest_rejects_fewer_than_two_points() {
        assertFailsWith<IllegalArgumentException> {
            MatchRequest(listOf(MatchPoint(lat = 12.9, lng = 77.6, radiusM = 5.0, timeMs = 1000L)))
        }
    }

    @Test
    fun matchedRoute_rejects_confidence_outside_zero_to_one() {
        assertFailsWith<IllegalArgumentException> {
            MatchedRoute(distanceM = 100.0, confidence = 1.5, pointMatched = listOf(true))
        }
        assertFailsWith<IllegalArgumentException> {
            MatchedRoute(distanceM = 100.0, confidence = -0.1, pointMatched = listOf(true))
        }
    }

    @Test
    fun matchedRoute_rejects_a_negative_distance() {
        assertFailsWith<IllegalArgumentException> {
            MatchedRoute(distanceM = -1.0, confidence = 0.5, pointMatched = listOf(true))
        }
    }

    @Test
    fun matchedRoute_carries_per_point_matched_flags_for_outliers() {
        // OSRM: a null tracepoint means the point could not be matched at all.
        val route = MatchedRoute(
            distanceM = 500.0,
            confidence = 0.8,
            pointMatched = listOf(true, false, true),
        )
        assertEquals(listOf(true, false, true), route.pointMatched)
    }
}
