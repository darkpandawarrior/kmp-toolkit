package com.siddharth.kmp.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The reactive read path: turns a data [Flow] + a connectivity [Flow] into a `Flow<ScreenState<T>>` by
 * running [DecisionEngine.decide] on each emission. This is the live form of the pure decision core — a
 * ViewModel collects this and exposes it as UI state.
 *
 * Deliberately Store-library-agnostic: the caller supplies both flows (their repository's cache+network
 * merge, and a connectivity source such as `:network`'s ConnectivityChecker). [FetchPolicy] shapes what
 * the caller feeds into [storeData] upstream (cache-only, network-only, periodic) — this combinator maps
 * whatever arrives, so it stays policy-agnostic and pure of side effects.
 *
 * @param now injectable clock (defaults to the system clock) so freshness/decisions are testable.
 */
@OptIn(ExperimentalTime::class)
fun <T> screenStateStream(
    storeData: Flow<StoreData<T>>,
    connectivity: Flow<Connectivity>,
    ttl: Duration,
    now: () -> Instant = { Clock.System.now() },
    classify: (Throwable) -> ErrorKind = { ErrorKind.Other },
): Flow<ScreenState<T>> =
    combine(storeData, connectivity) { data, conn ->
        DecisionEngine.decide(data, conn, ttl, now(), classify)
    }.distinctUntilChanged()

/**
 * Sibling stream: the per-card [FreshnessBand] over time, computed purely from `(fetchedAt, ttl,
 * lastError)` — network state is deliberately NOT an input (a separate connectivity banner owns that).
 * Run alongside [screenStateStream] to drive a freshness indicator that updates independently of the
 * content decision.
 */
@OptIn(ExperimentalTime::class)
fun <T> freshnessStream(
    storeData: Flow<StoreData<T>>,
    ttl: Duration,
    now: () -> Instant = { Clock.System.now() },
): Flow<FreshnessBand> =
    storeData
        .map { data -> freshnessBand(now(), data.fetchedAt, ttl, data.error) }
        .distinctUntilChanged()
