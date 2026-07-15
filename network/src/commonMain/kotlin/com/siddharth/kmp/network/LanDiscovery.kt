package com.siddharth.kmp.network

import kotlinx.coroutines.flow.Flow

/**
 * A service advertised on the local network, as seen by a discovering peer.
 *
 * Carries what a peer needs to reach the advertiser: where it is ([host]:[port]), an opaque
 * [payload] the advertiser attached (e.g. a room/session code, a token, arbitrary metadata), and a
 * human-friendly [name] for a "nearby" list. Only public reachability data should ever be advertised.
 */
data class LanHost(
    /** Resolvable host address (IPv4/IPv6 literal or mDNS hostname) of the advertising service. */
    val host: String,
    /** TCP port the advertised service listens on. */
    val port: Int,
    /** Opaque application payload attached to the advertisement (room code, token, metadata, …). */
    val payload: String,
    /** Human-friendly service name for display (e.g. "Sid's table"). */
    val name: String,
)

/**
 * Advertises a service on the local network so nearby peers can discover it.
 *
 * [serviceType] namespaces the advertisement. On Android NSD and iOS/Bonjour it is the DNS-SD service
 * type (e.g. `"_kursi._tcp"`); the JVM UDP beacon uses it as a magic wire token so foreign traffic and
 * other services on the shared beacon port are ignored. Two apps that pick distinct service types can
 * coexist on one subnet. Changing it is a breaking change to discovery compatibility.
 *
 * Platform actuals:
 *  - **android** — [android.net.nsd.NsdManager] (DNS-SD). Needs an app [android.content.Context]
 *    installed once via `installLanContext(...)`; without it, advertise/discover degrade to no-ops.
 *  - **ios**     — `NSNetService` / Bonjour.
 *  - **jvm**     — a real UDP-broadcast beacon ([java.net.DatagramSocket]) on [LAN_UDP_PORT].
 *  - **wasm**    — safe no-op (browsers cannot open raw sockets); start/stop succeed and do nothing.
 *
 * Lifecycle: construct with a [serviceType], call [start] with the coordinates, and [stop] when done.
 * Implementations are NOT required to be restartable after [stop]; create a new instance per ad.
 */
expect class LanAdvertiser(serviceType: String) {
    /**
     * Begins advertising [host]:[port] / [payload] under [serviceName]. Safe to call once; calling
     * again before [stop] is implementation-defined (prefer one instance per advertisement).
     */
    fun start(
        serviceName: String,
        payload: String,
        port: Int,
    )

    /** Stops advertising and releases platform resources. Idempotent. */
    fun stop()
}

/**
 * Discovers services of [serviceType] advertised on the local network.
 *
 * [discover] returns a cold [Flow] that emits a [LanHost] each time a new host is found (de-duplicated
 * by host+port+payload for the lifetime of the collection). Cancelling the collector tears down the
 * platform listener. The JVM actual additionally sends a one-shot UDP "probe" so already-advertising
 * hosts answer immediately rather than only on their next beacon tick.
 */
expect class LanDiscoverer(serviceType: String) {
    fun discover(): Flow<LanHost>
}
