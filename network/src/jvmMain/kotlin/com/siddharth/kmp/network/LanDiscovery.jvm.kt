package com.siddharth.kmp.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

// ─────────────────────────────────────────────────────────────────────────────
// JVM / desktop LAN discovery — a REAL UDP-broadcast beacon.
//
// Why UDP broadcast rather than JmDNS: it has zero extra dependencies (pure java.net), is trivially
// testable on loopback, and is robust on the flat home/office LANs this targets. The wire format is a
// single ASCII line so any implementation can parse it, prefixed by the SERVICE TYPE so foreign UDP
// traffic and other services on the shared beacon port are ignored:
//
//     <serviceType>|<payload>|<port>|<serviceName>
//
// The advertiser blasts the beacon on a fixed cadence; the discoverer listens AND fires a one-shot
// "probe" (`<serviceType>?`) so live hosts answer immediately instead of only on their next tick.
//
// ponytail: one shared UDP port for all service types; distinct types coexist because every packet is
// tagged with (and filtered by) its serviceType. Derive a per-type port only if cross-service beacon
// volume on one subnet ever becomes a real problem.
// ─────────────────────────────────────────────────────────────────────────────

/** The UDP port the JVM/desktop broadcast beacon advertises and listens on. */
const val LAN_UDP_PORT: Int = 45_678

private const val BEACON_INTERVAL_MS = 1_000L

private fun probeToken(serviceType: String): String = "$serviceType?"

private fun beaconPrefix(serviceType: String): String = "$serviceType|"

private fun encodeBeacon(
    serviceType: String,
    payload: String,
    port: Int,
    serviceName: String,
): String = "${beaconPrefix(serviceType)}$payload|$port|$serviceName"

private fun decodeBeacon(
    serviceType: String,
    raw: String,
    sourceHost: String,
): LanHost? {
    val prefix = beaconPrefix(serviceType)
    if (!raw.startsWith(prefix)) return null
    val parts = raw.removePrefix(prefix).split("|")
    if (parts.size < 3) return null
    val port = parts[1].toIntOrNull() ?: return null
    return LanHost(host = sourceHost, port = port, payload = parts[0], name = parts[2])
}

actual class LanAdvertiser actual constructor(
    private val serviceType: String,
) {
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null

    actual fun start(
        serviceName: String,
        payload: String,
        port: Int,
    ) {
        if (!running.compareAndSet(false, true)) return
        val sock = DatagramSocket().apply { broadcast = true }
        socket = sock
        val bytes = encodeBeacon(serviceType, payload, port, serviceName).toByteArray(Charsets.UTF_8)
        val broadcastAddr = InetAddress.getByName("255.255.255.255")
        val target = InetSocketAddress(broadcastAddr, LAN_UDP_PORT)
        val probeToken = probeToken(serviceType)

        worker =
            thread(name = "lan-advertiser", isDaemon = true) {
                // Listen for probes on the discovery port too, so we answer instantly. We use a SECOND
                // socket bound to the well-known port with reuse so multiple hosts on one machine coexist
                // (mainly for tests); if the bind fails we still beacon periodically.
                val responder =
                    try {
                        DatagramSocket(null).apply {
                            reuseAddress = true
                            broadcast = true
                            bind(InetSocketAddress(LAN_UDP_PORT))
                            soTimeout = BEACON_INTERVAL_MS.toInt()
                        }
                    } catch (_: Exception) {
                        null
                    }
                val probeBuf = ByteArray(64)
                try {
                    while (running.get() && !Thread.currentThread().isInterrupted) {
                        // 1. Periodic broadcast.
                        runCatching { sock.send(DatagramPacket(bytes, bytes.size, target)) }
                        // 2. Answer any probe that arrived this interval (unicast back to the asker).
                        if (responder != null) {
                            val probe = DatagramPacket(probeBuf, probeBuf.size)
                            val got =
                                runCatching {
                                    responder.receive(probe)
                                    true
                                }.getOrDefault(false)
                            if (got) {
                                val text = String(probe.data, 0, probe.length, Charsets.UTF_8)
                                if (text.startsWith(probeToken)) {
                                    runCatching {
                                        responder.send(
                                            DatagramPacket(bytes, bytes.size, probe.socketAddress),
                                        )
                                    }
                                }
                            }
                        } else {
                            Thread.sleep(BEACON_INTERVAL_MS)
                        }
                    }
                } finally {
                    responder?.close()
                }
            }
    }

    actual fun stop() {
        if (!running.compareAndSet(true, false)) return
        worker?.interrupt()
        worker = null
        socket?.close()
        socket = null
    }
}

actual class LanDiscoverer actual constructor(
    private val serviceType: String,
) {
    actual fun discover(): Flow<LanHost> =
        callbackFlow {
            val socket =
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(0)) // ephemeral local port for receiving unicast probe answers
                    soTimeout = 500
                }

            // Fire a one-shot probe so already-running hosts reply immediately.
            runCatching {
                val probe = probeToken(serviceType).toByteArray(Charsets.UTF_8)
                socket.send(
                    DatagramPacket(
                        probe,
                        probe.size,
                        InetSocketAddress(InetAddress.getByName("255.255.255.255"), LAN_UDP_PORT),
                    ),
                )
            }

            val seen = HashSet<String>()
            val listening = AtomicBoolean(true)

            // We also need to hear PERIODIC broadcasts (sent to LAN_UDP_PORT). Bind a second socket to
            // that well-known port with reuseAddress so it coexists with an advertiser on the same host.
            val beaconSocket =
                try {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        bind(InetSocketAddress(LAN_UDP_PORT))
                        soTimeout = 500
                    }
                } catch (_: Exception) {
                    null
                }

            val buf = ByteArray(256)

            fun pump(sock: DatagramSocket) {
                while (listening.get()) {
                    val packet = DatagramPacket(buf, buf.size)
                    val received =
                        runCatching {
                            sock.receive(packet)
                            true
                        }.getOrDefault(false)
                    if (!received) continue // socket timeout — loop and re-check `listening`
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val sourceHost = packet.address?.hostAddress ?: continue
                    val hostRecord = decodeBeacon(serviceType, text, sourceHost) ?: continue
                    val key = "${hostRecord.host}:${hostRecord.port}/${hostRecord.payload}"
                    if (seen.add(key)) trySend(hostRecord)
                }
            }

            val t1 = thread(name = "lan-discoverer-unicast", isDaemon = true) { pump(socket) }
            val t2 =
                beaconSocket?.let { bs ->
                    thread(name = "lan-discoverer-beacon", isDaemon = true) { pump(bs) }
                }

            awaitClose {
                listening.set(false)
                socket.close()
                beaconSocket?.close()
                t1.interrupt()
                t2?.interrupt()
            }
        }.flowOn(Dispatchers.IO)
}
