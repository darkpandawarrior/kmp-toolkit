package com.siddharth.kmp.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// ─────────────────────────────────────────────────────────────────────────────
// Android LAN discovery — Network Service Discovery (NSD / DNS-SD).
//
// NSD needs a Context to obtain the NsdManager. Since the expect class only carries a service type
// (KMP common shape), we resolve the application Context from a process-wide holder that the host app
// installs once at startup via [installLanContext]. This keeps the common API Context-free while the
// Android actual still gets what the platform requires. If no Context was installed, start/discover
// degrade to safe no-ops rather than crashing (e.g. unit tests on the JVM that touch this source set).
// ─────────────────────────────────────────────────────────────────────────────

private var appContextHolder: Context? = null

/**
 * Install the application [Context] used by the Android NSD actuals. Call once from
 * `Application.onCreate()` (or the compose entrypoint) BEFORE advertising/discovering.
 */
fun installLanContext(context: Context) {
    appContextHolder = context.applicationContext
}

private fun nsdManager(): NsdManager? = (appContextHolder?.getSystemService(Context.NSD_SERVICE)) as? NsdManager

/** NSD service types are of the form "_kursi._tcp." — NSD wants a trailing dot. */
private fun String.asNsdType(): String = if (endsWith(".")) this else "$this."

actual class LanAdvertiser actual constructor(
    private val serviceType: String,
) {
    private var manager: NsdManager? = null
    private var listener: NsdManager.RegistrationListener? = null

    actual fun start(
        serviceName: String,
        payload: String,
        port: Int,
    ) {
        val mgr = nsdManager() ?: return
        manager = mgr
        val info =
            NsdServiceInfo().apply {
                this.serviceName = serviceName
                this.serviceType = this@LanAdvertiser.serviceType.asNsdType()
                this.port = port
                // The payload travels as a TXT attribute so a discovering peer learns how to reach the host.
                setAttribute(TXT_PAYLOAD, payload)
            }
        val reg =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {}

                override fun onRegistrationFailed(
                    info: NsdServiceInfo,
                    errorCode: Int,
                ) {}

                override fun onServiceUnregistered(info: NsdServiceInfo) {}

                override fun onUnregistrationFailed(
                    info: NsdServiceInfo,
                    errorCode: Int,
                ) {}
            }
        listener = reg
        runCatching { mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, reg) }
    }

    actual fun stop() {
        val mgr = manager
        val reg = listener
        if (mgr != null && reg != null) runCatching { mgr.unregisterService(reg) }
        listener = null
        manager = null
    }

    companion object {
        const val TXT_PAYLOAD = "payload"
    }
}

actual class LanDiscoverer actual constructor(
    private val serviceType: String,
) {
    @SuppressLint("MissingPermission")
    actual fun discover(): Flow<LanHost> =
        callbackFlow {
            val mgr =
                nsdManager() ?: run {
                    close()
                    return@callbackFlow
                }

            val resolveListener =
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(
                        info: NsdServiceInfo,
                        errorCode: Int,
                    ) {}

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress ?: return
                        val payload =
                            info.attributes[LanAdvertiser.TXT_PAYLOAD]
                                ?.toString(Charsets.UTF_8) ?: ""
                        trySend(
                            LanHost(
                                host = host,
                                port = info.port,
                                payload = payload,
                                name = info.serviceName ?: "",
                            ),
                        )
                    }
                }

            val discoveryListener =
                object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) {}

                    override fun onStopDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) {}

                    override fun onDiscoveryStarted(serviceType: String) {}

                    override fun onDiscoveryStopped(serviceType: String) {}

                    override fun onServiceFound(info: NsdServiceInfo) {
                        // Each found service must be resolved to obtain its host/port/TXT records.
                        runCatching { mgr.resolveService(info, resolveListener) }
                    }

                    override fun onServiceLost(info: NsdServiceInfo) {}
                }

            runCatching {
                mgr.discoverServices(serviceType.asNsdType(), NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            }

            awaitClose {
                runCatching { mgr.stopServiceDiscovery(discoveryListener) }
            }
        }
}
