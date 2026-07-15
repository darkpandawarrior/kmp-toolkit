package com.siddharth.kmp.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

// ─────────────────────────────────────────────────────────────────────────────
// wasmJs LAN discovery — a safe NO-OP.
//
// A browser sandbox cannot open raw UDP sockets or use mDNS/Bonjour, so there is no way to advertise
// or discover hosts on the local network from wasm. Rather than fail to compile (which would break the
// "compiles on ALL targets" gate) or throw at runtime, the wasm actuals are inert: [LanAdvertiser.start]
// /[stop] do nothing and [LanDiscoverer.discover] emits no hosts. A web client reaches a service by
// entering an address/code directly instead.
// ─────────────────────────────────────────────────────────────────────────────

actual class LanAdvertiser actual constructor(
    @Suppress("unused") private val serviceType: String,
) {
    actual fun start(
        serviceName: String,
        payload: String,
        port: Int,
    ) { /* no-op: browser sandbox */ }

    actual fun stop() { /* no-op */ }
}

actual class LanDiscoverer actual constructor(
    @Suppress("unused") private val serviceType: String,
) {
    actual fun discover(): Flow<LanHost> = emptyFlow()
}
