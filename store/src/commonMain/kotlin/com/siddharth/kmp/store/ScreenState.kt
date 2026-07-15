package com.siddharth.kmp.store

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The one UI state an offline-first screen renders. Collapses the usual scatter of `isLoading` /
 * `isFromCache` / `isRefreshing` / `error` / `networkStatus` flags into a single exhaustive type a
 * `when` can render. Produced by [DecisionEngine.decide] from a [StoreData] snapshot + [Connectivity].
 *
 * Original implementation of the offline-first screen-state pattern — the concept is a common one; this
 * is a clean-room, dependency-free take (no Store5, no HTTP client) for the MIT toolkit.
 */
sealed interface ScreenState<out T> {
    /** Initial load — nothing to show yet. */
    data object Loading : ScreenState<Nothing>

    /** A fetch completed but returned no content (e.g. an empty list). */
    data object Empty : ScreenState<Nothing>

    /** Offline with no cached data. [isCaptivePortal] is true behind a hotel/airport WiFi login wall. */
    data class NoNetwork(val isCaptivePortal: Boolean = false) : ScreenState<Nothing>

    /** Auth required (401/403 / token expiry) with no usable cache — send the user to sign-in. */
    data object Unauthenticated : ScreenState<Nothing>

    /** A non-network error with no usable cache. */
    data class Error(val error: Throwable, val isNetworkError: Boolean = false) : ScreenState<Nothing>

    /** Data to show, with freshness metadata. [isRefreshing] drives a background-refresh indicator. */
    @OptIn(ExperimentalTime::class)
    data class Content<T>(
        val data: T,
        val fetchedAt: Instant? = null,
        val freshness: FreshnessBand = FreshnessBand.Initial,
        val isRefreshing: Boolean = false,
    ) : ScreenState<T>
}

/**
 * A snapshot from the caller's data pipeline: the latest [data] (or null), the last [error], when it
 * was last successfully fetched ([fetchedAt]), and whether a refresh is in flight. Deliberately not
 * tied to any store library — a repository fills this in however it likes.
 */
@OptIn(ExperimentalTime::class)
data class StoreData<out T>(
    val data: T?,
    val error: Throwable? = null,
    val fetchedAt: Instant? = null,
    val isRefreshing: Boolean = false,
) {
    /** No usable content — null, or an empty collection/map. */
    val isEmpty: Boolean
        get() =
            when (val d = data) {
                null -> true
                is Collection<*> -> d.isEmpty()
                is Map<*, *> -> d.isEmpty()
                else -> false
            }
}

/** Connectivity as the decision core sees it. [CaptivePortal] is "linked but walled off". */
enum class Connectivity { Online, Offline, CaptivePortal }

/** Coarse error classification the [DecisionEngine] routes on. The caller supplies the classifier. */
enum class ErrorKind { Network, Auth, Other }
