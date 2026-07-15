package com.siddharth.kmp.store

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The heart of the offline-first read path: a PURE function from `(data snapshot, connectivity)` to the
 * single [ScreenState] a screen renders. No coroutines, no I/O — exhaustively unit-testable.
 *
 * Error routing is delegated to a caller-supplied [ErrorKind] classifier so this stays decoupled from
 * any HTTP client / exception hierarchy (the default treats every error as [ErrorKind.Other]).
 */
object DecisionEngine {
    /**
     * @param storeData latest snapshot (data / error / fetchedAt / isRefreshing).
     * @param connectivity current link state; [Connectivity.CaptivePortal] counts as offline for
     *   content decisions but surfaces a distinct flag.
     * @param ttl freshness bound for the [ScreenState.Content] band.
     * @param now current instant (injected for testability).
     * @param classify maps an error to a [ErrorKind]; drives the no-data error branch.
     */
    @OptIn(ExperimentalTime::class)
    fun <T> decide(
        storeData: StoreData<T>,
        connectivity: Connectivity,
        ttl: Duration,
        now: Instant,
        classify: (Throwable) -> ErrorKind = { ErrorKind.Other },
    ): ScreenState<T> {
        val error = storeData.error

        if (storeData.isEmpty) {
            return when {
                connectivity == Connectivity.CaptivePortal -> ScreenState.NoNetwork(isCaptivePortal = true)
                connectivity == Connectivity.Offline -> ScreenState.NoNetwork()
                error != null ->
                    when (classify(error)) {
                        ErrorKind.Network -> ScreenState.NoNetwork()
                        ErrorKind.Auth -> ScreenState.Unauthenticated
                        ErrorKind.Other -> ScreenState.Error(error, isNetworkError = false)
                    }
                // Fetched successfully but nothing came back → Empty; never fetched → still Loading.
                storeData.fetchedAt != null -> ScreenState.Empty
                else -> ScreenState.Loading
            }
        }

        return ScreenState.Content(
            data = requireNotNull(storeData.data) { "isEmpty guarantees non-null data here" },
            fetchedAt = storeData.fetchedAt,
            freshness = freshnessBand(now, storeData.fetchedAt, ttl, error),
            isRefreshing = storeData.isRefreshing,
        )
    }
}
