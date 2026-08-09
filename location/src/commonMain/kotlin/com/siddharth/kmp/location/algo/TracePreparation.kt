package com.siddharth.kmp.location.algo

import kotlin.math.min

/**
 * Turn a raw fix stream into something a map-matching provider can actually be asked about.
 *
 * Two problems, both pure and both testable without a network:
 *
 * 1. **Cadence.** OSRM's own documented failure mode is a trace that is too *dense*: a tight
 *    sampling rate can fragment into several disconnected matchings instead of one continuous
 *    route. More points is not automatically better — [downsample] thins a trace toward a target
 *    interval instead of sending everything recorded.
 * 2. **Size.** A long trip has more points than one request should carry. [chunk] splits it into
 *    windows — WITH overlap, because a matcher needs a few points either side of a boundary to
 *    place it on the right road; a hard cut at the window edge starves the matcher of exactly the
 *    context it needs there. The overlap then has to be un-double-counted on the way back out,
 *    which is what [stitchMatchedDistanceM] is for.
 */
public object TracePreparation {

    /**
     * Thin [fixes] so consecutive kept points are at least [targetIntervalSec] apart, without
     * moving or dropping the first or last fix.
     *
     * Endpoints are preserved unconditionally because they anchor every downstream distance
     * comparison — a trip whose downsampled trace starts or ends somewhere other than where the
     * recorded trip actually started or ended would make every reconciliation against a client
     * figure meaningless before the matcher is even called.
     *
     * ponytail: a simple time gate, not a Douglas-Peucker-style geometric simplifier. A geometric
     * simplifier optimizes for shape fidelity; what a map matcher needs is a sane number of points
     * per unit time. Revisit only if OSRM keeps fragmenting matches on a gated trace.
     */
    public fun downsample(fixes: List<Fix>, targetIntervalSec: Double): List<Fix> {
        require(targetIntervalSec > 0.0) { "targetIntervalSec must be positive, got $targetIntervalSec" }
        if (fixes.size <= 2) return fixes

        val kept = ArrayList<Fix>(fixes.size)
        kept.add(fixes.first())
        var lastKeptMs = fixes.first().timeMs
        for (i in 1 until fixes.size - 1) {
            val f = fixes[i]
            if ((f.timeMs - lastKeptMs) / 1000.0 >= targetIntervalSec) {
                kept.add(f)
                lastKeptMs = f.timeMs
            }
        }
        kept.add(fixes.last())
        return kept
    }

    /**
     * Split [fixes] into request-sized windows, each sharing up to [overlapPoints] points with the
     * previous window.
     *
     * [TraceChunk.overlapWithPrevious] tells the caller — and [stitchMatchedDistanceM] — exactly how
     * many of a chunk's leading points are a rerun of the previous chunk's trailing points, so
     * nothing downstream has to re-derive it from timestamps.
     */
    public fun chunk(
        fixes: List<Fix>,
        maxPointsPerChunk: Int,
        overlapPoints: Int,
    ): List<TraceChunk> {
        require(maxPointsPerChunk >= 2) {
            "maxPointsPerChunk must allow at least a 2-point chunk, got $maxPointsPerChunk"
        }
        require(overlapPoints in 0 until maxPointsPerChunk) {
            "overlapPoints ($overlapPoints) must be less than maxPointsPerChunk ($maxPointsPerChunk)"
        }
        if (fixes.isEmpty()) return emptyList()
        if (fixes.size <= maxPointsPerChunk) return listOf(TraceChunk(fixes, overlapWithPrevious = 0))

        val stride = maxPointsPerChunk - overlapPoints
        val chunks = ArrayList<TraceChunk>()
        var start = 0
        var first = true
        while (start < fixes.size) {
            val end = min(start + maxPointsPerChunk, fixes.size)
            val overlap = if (first) 0 else min(overlapPoints, end - start)
            chunks.add(TraceChunk(fixes.subList(start, end).toList(), overlap))
            if (end == fixes.size) break
            start += stride
            first = false
        }
        return chunks
    }

    /**
     * Recombine one [MatchedRoute] per [chunks] entry into a single trip distance, counting the
     * overlap between consecutive chunks exactly once.
     *
     * **The subtle part.** A chunk with `overlapWithPrevious = k` shares its first `k` points —
     * and therefore its first `k - 1` legs — with the tail of the chunk before it. Those `k - 1`
     * legs were already added when the previous chunk was folded in, so this drops exactly that
     * many legs from the front of every chunk after the first before summing the rest. Get the
     * count wrong in either direction and every multi-chunk trip is silently short or long by the
     * overlap's own distance — the kind of error that never shows up on a single-chunk trace, only
     * in production on the trips long enough to need chunking at all.
     *
     * Requires [MatchedRoute.legDistancesM] on every chunk; a route that cannot report legs makes
     * correct stitching impossible, so this refuses rather than approximate one from the aggregate
     * [MatchedRoute.distanceM].
     */
    public fun stitchMatchedDistanceM(chunks: List<TraceChunk>, routes: List<MatchedRoute>): Double {
        require(chunks.size == routes.size) {
            "chunks (${chunks.size}) and routes (${routes.size}) must be the same size — one route per chunk"
        }
        var total = 0.0
        for (i in chunks.indices) {
            val expectedLegs = (chunks[i].fixes.size - 1).coerceAtLeast(0)
            val legs = routes[i].legDistancesM
            require(legs.size == expectedLegs) {
                "chunk $i has ${chunks[i].fixes.size} point(s) (needs $expectedLegs leg distances) " +
                    "but its route reported ${legs.size}"
            }
            val skip = (chunks[i].overlapWithPrevious - 1).coerceAtLeast(0)
            total += legs.drop(skip).sum()
        }
        return total
    }
}

/**
 * One request-sized window of a longer trace.
 *
 * [overlapWithPrevious] is 0 for the first chunk and otherwise the number of leading [fixes] that
 * duplicate the trailing fixes of the chunk before it — the exact count [TracePreparation.stitchMatchedDistanceM]
 * needs to avoid double-counting.
 */
public data class TraceChunk(
    val fixes: List<Fix>,
    val overlapWithPrevious: Int,
)
