package com.siddharth.kmp.network

import dev.tmapps.konnection.Konnection
import kotlinx.coroutines.flow.Flow

/**
 * Real reachability via Konnection (NWPathMonitor-backed on iOS 12+, SCNetworkReachability
 * fallback below that). Replaces [AlwaysOnlineConnectivityChecker] for consumers that want an
 * actual signal — Konnection self-initializes on first [Konnection.instance] access, no manual
 * createInstance() call needed on this platform.
 */
class KonnectionConnectivityChecker : ConnectivityChecker {
    override fun isOnline(): Boolean = Konnection.instance.isConnected()

    override fun observeIsOnline(): Flow<Boolean> = Konnection.instance.observeHasConnection()
}
