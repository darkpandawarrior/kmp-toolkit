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
}

/**
 * Default used on jvm/ios. ponytail: assumes online and lets the HTTP call fail-and-retry rather
 * than probing reachability — upgrade to NWPathMonitor (ios) / a real jvm probe if false-positives
 * ever cost a bad UX.
 */
object AlwaysOnlineConnectivityChecker : ConnectivityChecker {
    override fun isOnline(): Boolean = true
}
