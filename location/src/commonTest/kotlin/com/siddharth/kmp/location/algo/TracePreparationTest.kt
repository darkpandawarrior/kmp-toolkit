package com.siddharth.kmp.location.algo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TracePreparationTest {

    // ~111.19 m per 0.001 deg of latitude at the equator — same constant TraceCodecTest uses.
    private fun straightLine(count: Int, stepSec: Long = 5): List<Fix> =
        (0 until count).map {
            Fix(lat = 12.9 + it * 0.001, lng = 77.6, timeMs = 1000L + it * stepSec * 1000L, accuracyM = 6.0)
        }

    // ── downsample ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun downsample_keeps_first_and_last_fix_even_when_densely_sampled() {
        val fixes = straightLine(50, stepSec = 1)
        val thinned = TracePreparation.downsample(fixes, targetIntervalSec = 10.0)

        assertEquals(fixes.first(), thinned.first())
        assertEquals(fixes.last(), thinned.last())
        assertTrue(thinned.size < fixes.size, "expected thinning, got ${thinned.size} of ${fixes.size}")
    }

    @Test
    fun downsample_drops_points_closer_than_the_target_interval() {
        val fixes = straightLine(20, stepSec = 1)
        val thinned = TracePreparation.downsample(fixes, targetIntervalSec = 5.0)

        for (i in 1 until thinned.size - 1) {
            val gapSec = (thinned[i].timeMs - thinned[i - 1].timeMs) / 1000.0
            assertTrue(gapSec >= 5.0, "kept points $gapSec s apart, wanted >= 5.0s: $thinned")
        }
    }

    @Test
    fun downsample_leaves_a_trace_of_two_or_fewer_fixes_untouched() {
        assertEquals(emptyList(), TracePreparation.downsample(emptyList(), 5.0))
        val one = straightLine(1)
        assertEquals(one, TracePreparation.downsample(one, 5.0))
        val two = straightLine(2)
        assertEquals(two, TracePreparation.downsample(two, 5.0))
    }

    @Test
    fun downsample_rejects_a_non_positive_target_interval() {
        assertFailsWith<IllegalArgumentException> { TracePreparation.downsample(straightLine(5), 0.0) }
    }

    // ── chunk ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun chunk_of_an_empty_trace_is_empty() {
        assertEquals(emptyList(), TracePreparation.chunk(emptyList(), maxPointsPerChunk = 5, overlapPoints = 1))
    }

    @Test
    fun chunk_of_a_trace_smaller_than_the_window_is_a_single_unoverlapped_chunk() {
        val fixes = straightLine(3)
        val chunks = TracePreparation.chunk(fixes, maxPointsPerChunk = 10, overlapPoints = 2)

        assertEquals(1, chunks.size)
        assertEquals(fixes, chunks.single().fixes)
        assertEquals(0, chunks.single().overlapWithPrevious)
    }

    @Test
    fun chunk_rejects_overlap_that_is_not_smaller_than_the_window() {
        assertFailsWith<IllegalArgumentException> {
            TracePreparation.chunk(straightLine(20), maxPointsPerChunk = 4, overlapPoints = 4)
        }
    }

    @Test
    fun chunk_covers_every_fix_and_marks_the_first_chunk_as_unoverlapped() {
        val fixes = straightLine(25)
        val chunks = TracePreparation.chunk(fixes, maxPointsPerChunk = 10, overlapPoints = 3)

        assertTrue(chunks.size > 1, "expected multiple chunks for 25 fixes")
        assertEquals(0, chunks.first().overlapWithPrevious)
        assertEquals(fixes.last(), chunks.last().fixes.last(), "last chunk must reach the end of the trace")
        chunks.drop(1).forEach { assertTrue(it.overlapWithPrevious > 0, "expected every later chunk to overlap") }
    }

    // ── stitchMatchedDistanceM: the subtle part ────────────────────────────────────────────────

    /** Builds a "perfect" [MatchedRoute] for a chunk: leg distances equal to the raw haversine legs. */
    private fun perfectRoute(chunk: TraceChunk): MatchedRoute {
        val legs = chunk.fixes.zipWithNext { a, b -> haversineMeters(a.lat, a.lng, b.lat, b.lng) }
        return MatchedRoute(
            distanceM = legs.sum(),
            confidence = 1.0,
            pointMatched = chunk.fixes.map { true },
            legDistancesM = legs,
        )
    }

    private fun trueDistanceM(fixes: List<Fix>): Double =
        fixes.zipWithNext { a, b -> haversineMeters(a.lat, a.lng, b.lat, b.lng) }.sum()

    @Test
    fun stitching_a_chunked_trace_matches_the_unchunked_ground_truth() {
        val fixes = straightLine(25)
        val truth = trueDistanceM(fixes)

        val chunks = TracePreparation.chunk(fixes, maxPointsPerChunk = 10, overlapPoints = 3)
        val routes = chunks.map(::perfectRoute)

        val stitched = TracePreparation.stitchMatchedDistanceM(chunks, routes)
        assertEquals(truth, stitched, absoluteTolerance = 0.001)
    }

    @Test
    fun stitching_without_chunking_is_a_no_op() {
        val fixes = straightLine(7)
        val chunks = TracePreparation.chunk(fixes, maxPointsPerChunk = 100, overlapPoints = 5)
        val stitched = TracePreparation.stitchMatchedDistanceM(chunks, chunks.map(::perfectRoute))

        assertEquals(trueDistanceM(fixes), stitched, absoluteTolerance = 0.001)
    }

    @Test
    fun stitching_a_single_point_chunk_contributes_zero_distance() {
        val chunks = listOf(TraceChunk(fixes = straightLine(1), overlapWithPrevious = 0))
        val routes = listOf(MatchedRoute(distanceM = 0.0, confidence = 1.0, pointMatched = listOf(true)))

        assertEquals(0.0, TracePreparation.stitchMatchedDistanceM(chunks, routes))
    }

    @Test
    fun stitching_refuses_a_route_missing_leg_distances_instead_of_guessing() {
        val chunks = TracePreparation.chunk(straightLine(5), maxPointsPerChunk = 3, overlapPoints = 1)
        // Aggregate distance only, no legs — a real provider that can't report legs.
        val routes = chunks.map { MatchedRoute(distanceM = 400.0, confidence = 1.0, pointMatched = it.fixes.map { true }) }

        val e = assertFailsWith<IllegalArgumentException> { TracePreparation.stitchMatchedDistanceM(chunks, routes) }
        assertTrue(e.message!!.contains("leg distances"), e.message!!)
    }

    @Test
    fun stitching_requires_one_route_per_chunk() {
        val chunks = TracePreparation.chunk(straightLine(5), maxPointsPerChunk = 3, overlapPoints = 1)
        assertFailsWith<IllegalArgumentException> {
            TracePreparation.stitchMatchedDistanceM(chunks, listOf(perfectRoute(chunks.first())))
        }
    }
}
