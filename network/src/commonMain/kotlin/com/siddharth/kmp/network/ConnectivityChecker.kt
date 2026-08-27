package com.siddharth.kmp.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Cheap online check for the offline-first sync (gate a `refresh()` before hitting the network).
 * An interface, not expect/actual, because only the Android/iOS/jvm impls need a platform
 * dependency (ConnectivityManager / Konnection); wasmJs uses the naive default. Koin binds the
 * right one.
 */
interface ConnectivityChecker {
    fun isOnline(): Boolean

    /**
     * Live connectivity observer. Default is a single-shot [Flow] of the current [isOnline] value
     * (no push updates) — override for a real observer. [KonnectionConnectivityChecker] does.
     */
    fun observeIsOnline(): Flow<Boolean> = flowOf(isOnline())

    /**
     * Whether [observeIsOnline] is a real push observer or the single-shot default above.
     *
     * This exists for one consumer contract: `offline-outbox` drains on the [observeIsOnline] Flow
     * when this is true, and keeps its timer retry when it is false. Without the distinction the
     * outbox cannot tell a live signal from one value that never changes, and on a platform with
     * no observer it would subscribe once and then silently stop flushing forever.
     *
     * Defaults to false, so a new implementation that forgets to override it degrades to the
     * timer rather than to silence.
     */
    fun canObserveConnectivity(): Boolean = false
}

/**
 * Default used on jvm/ios. ponytail: assumes online and lets the HTTP call fail-and-retry rather
 * than probing reachability — upgrade to NWPathMonitor (ios) / a real jvm probe if false-positives
 * ever cost a bad UX.
 */
object AlwaysOnlineConnectivityChecker : ConnectivityChecker {
    override fun isOnline(): Boolean = true
}
