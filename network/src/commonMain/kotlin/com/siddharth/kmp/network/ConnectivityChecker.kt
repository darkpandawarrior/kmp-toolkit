package com.siddharth.kmp.network

/**
 * Cheap online check for the offline-first sync (gate a `refresh()` before hitting the network).
 * An interface, not expect/actual, because only the Android impl needs a platform dependency
 * (ConnectivityManager via Context); other platforms use the naive default. Koin binds the right one.
 */
interface ConnectivityChecker {
    fun isOnline(): Boolean
}

/**
 * Default used on jvm/ios. ponytail: assumes online and lets the HTTP call fail-and-retry rather
 * than probing reachability — upgrade to NWPathMonitor (ios) / a real jvm probe if false-positives
 * ever cost a bad UX.
 */
object AlwaysOnlineConnectivityChecker : ConnectivityChecker {
    override fun isOnline(): Boolean = true
}
