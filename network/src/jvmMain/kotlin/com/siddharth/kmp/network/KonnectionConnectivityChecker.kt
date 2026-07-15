package com.siddharth.kmp.network

import dev.tmapps.konnection.Konnection
import kotlinx.coroutines.flow.Flow

/**
 * Real reachability via Konnection (periodic network-interface probe on jvm — no Context needed,
 * unlike Android). Replaces [AlwaysOnlineConnectivityChecker] for consumers that want an actual
 * signal instead of an always-true stub.
 */
class KonnectionConnectivityChecker : ConnectivityChecker {
    override fun isOnline(): Boolean = Konnection.instance.isConnected()

    override fun observeIsOnline(): Flow<Boolean> = Konnection.instance.observeHasConnection()
}
