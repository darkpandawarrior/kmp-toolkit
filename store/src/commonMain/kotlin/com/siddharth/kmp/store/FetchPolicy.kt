package com.siddharth.kmp.store

/**
 * Whether a screen reads from cache, the network, or both — the read strategy a caller hands to its
 * store stream. Kept as a sealed interface so new variants can be added without breaking the
 * `FetchPolicy.NetworkWithCache` access pattern.
 *
 * | Scenario | Policy |
 * |---|---|
 * | Normal screen — show cache instantly, refresh in background | [NetworkWithCache] (default) |
 * | Must-be-fresh (payment status, post-transaction balance) | [NetworkOnly] |
 * | Explicit offline / "load from cache" | [CacheOnly] |
 * | Ambient surface that re-fetches on a cadence (tickers, FX) | [Periodic] |
 */
sealed interface FetchPolicy {
    /** Emit cache immediately (if any), then fetch and emit the refreshed value. The default. */
    data object NetworkWithCache : FetchPolicy

    /** Always hit the network; on failure emit [ScreenState.NoNetwork]/[ScreenState.Error], not stale data. */
    data object NetworkOnly : FetchPolicy

    /** Read cache only; never hit the network. Empty cache emits [ScreenState.Empty]. */
    data object CacheOnly : FetchPolicy

    /**
     * [NetworkWithCache] read semantics plus a periodic background refresh every [intervalMs].
     * [intervalMs] must be positive; sub-second cadences are wasteful on mobile.
     */
    data class Periodic(val intervalMs: Long) : FetchPolicy {
        init {
            require(intervalMs > 0) { "Periodic.intervalMs must be > 0, was $intervalMs" }
        }
    }
}
