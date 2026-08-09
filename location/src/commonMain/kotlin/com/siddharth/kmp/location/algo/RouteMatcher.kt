package com.siddharth.kmp.location.algo

/**
 * The seam between "distance we computed from raw fixes" and "distance a road network says we
 * drove".
 *
 * Every gate in [MileageAlgorithm] — jitter, spikes, stop suppression — removes *noise*: symmetric
 * error that shrinks over a long enough trip. GPS also carries a *bias* that noise-removal cannot
 * touch: a straight-line (haversine) leg between two fixes on a curving road, or between two fixes
 * either side of a switchback, is always short. Snapping the trace onto the road graph is the only
 * correction that runs in one direction, which is exactly why it belongs on the figure that ends up
 * on a reimbursement claim.
 *
 * This is a pure interface. `:location` has zero dependencies and compiles for android/jvm/iOS/
 * wasmJs, and there is no HTTP client that runs on all four — so the network round-trip (OSRM's
 * `/match/v1/{profile}/{coords}` or any equivalent) is implemented in the app or on a server, never
 * here. What lives here is everything that does NOT need a network: preparing the request
 * ([TracePreparation]) and deciding what a response means ([MatchReconciliation]).
 *
 * `match` is `suspend` because doing the round-trip synchronously would block a thread for however
 * long the network takes; `suspend` is a language feature, not the `kotlinx.coroutines` library, so
 * declaring it here costs this module nothing.
 */
public interface RouteMatcher {
    public suspend fun match(request: MatchRequest): MatchedRoute
}

/**
 * One point as a map-matching request wants it: a coordinate, how much to trust it, and when it
 * was recorded.
 *
 * [radiusM] should come from [Fix.accuracyM] — it tells the matcher how far from the raw point the
 * true road position is allowed to be. A tight radius on a noisy fix makes the matcher discard a
 * point it could have used; a loose radius on a good fix makes it snap to the wrong nearby road.
 * Passing the fix's own accuracy is the only value that is honest in both directions.
 */
public data class MatchPoint(
    val lat: Double,
    val lng: Double,
    val radiusM: Double,
    val timeMs: Long,
)

/**
 * Everything one map-matching request needs, serialization-agnostic.
 *
 * A caller turns this into whatever the transport wants — an OSRM query string
 * (`coordinates`/`radiuses`/`timestamps`, semicolon-joined) or a JSON body for another provider —
 * without this module knowing anything about HTTP or JSON.
 */
public data class MatchRequest(
    val points: List<MatchPoint>,
) {
    init {
        require(points.size >= 2) { "a match request needs at least 2 points, got ${points.size}" }
    }

    public companion object {
        public fun from(fixes: List<Fix>): MatchRequest =
            MatchRequest(fixes.map { MatchPoint(it.lat, it.lng, it.accuracyM, it.timeMs) })
    }
}

/**
 * What a map-matching provider reported for one [MatchRequest].
 *
 * [legDistancesM] is per-leg distance between consecutive points **in this response**, one shorter
 * than [MatchPoint] — the same shape as OSRM's `matchings[].legs[].distance`. It exists so
 * [TracePreparation.stitchMatchedDistanceM] can drop exactly the legs a chunk overlap duplicated
 * instead of guessing at a fraction of the chunk's aggregate [distanceM]. A [RouteMatcher] that
 * cannot report it should leave it empty — [TracePreparation.stitchMatchedDistanceM] refuses loudly
 * rather than silently mis-stitching.
 *
 * [confidence] collapses toward zero on sparse or ambiguous traces — a real 3-point OSRM match
 * returned `5.9e-05`. Never branch on this value alone as "trustworthy"; that decision belongs to
 * [MatchReconciliation], which also has the client's own figure to weigh it against.
 */
public data class MatchedRoute(
    val distanceM: Double,
    val confidence: Double,
    /** Same length as the request's points; `false` where the provider called the point an outlier
     * (OSRM: a `null` tracepoint). */
    val pointMatched: List<Boolean>,
    val legDistancesM: List<Double> = emptyList(),
) {
    init {
        require(distanceM >= 0.0) { "distanceM must not be negative, got $distanceM" }
        require(confidence in 0.0..1.0) { "confidence must be in [0,1], got $confidence" }
    }
}
