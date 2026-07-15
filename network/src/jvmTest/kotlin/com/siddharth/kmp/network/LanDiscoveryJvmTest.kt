package com.siddharth.kmp.network

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM LAN discovery test: a host advertises a service via the REAL UDP-broadcast beacon and a
 * discoverer finds it on the local loopback/subnet, recovering the host's payload + port.
 *
 * This exercises the actual [java.net.DatagramSocket] beacon (no mocking) — the same code a desktop
 * host runs. It runs on loopback so it is deterministic and CI-safe.
 */
class LanDiscoveryJvmTest {
    private val serviceType = "_kmptoolkit-lantest._tcp"

    @Test
    fun `discoverer finds an advertised host and recovers its join coordinates`() =
        runBlocking {
            val advertiser = LanAdvertiser(serviceType)
            try {
                advertiser.start(serviceName = "Sid's table", payload = "ABCD42", port = 8080)

                // The probe-on-discover path means a live host answers quickly; allow generous slack for CI.
                val host =
                    withTimeout(15_000) {
                        LanDiscoverer(serviceType).discover().first()
                    }

                assertEquals("ABCD42", host.payload, "discovered payload must match the advertised one")
                assertEquals(8080, host.port, "discovered port must match the advertised one")
                assertEquals("Sid's table", host.name, "discovered service name must match")
                assertTrue(host.host.isNotBlank(), "discovered host address must be resolvable/non-blank")
            } finally {
                advertiser.stop()
            }
        }

    @Test
    fun `beacon wire format round-trips payload port and name`() =
        runBlocking {
            // A second host with different coordinates must be distinguishable by the discoverer.
            val advertiser = LanAdvertiser(serviceType)
            try {
                advertiser.start(serviceName = "Table-2", payload = "ZZ9XY1", port = 9090)
                val host = withTimeout(15_000) { LanDiscoverer(serviceType).discover().first { it.payload == "ZZ9XY1" } }
                assertEquals(9090, host.port)
                assertEquals("Table-2", host.name)
            } finally {
                advertiser.stop()
            }
        }
}
