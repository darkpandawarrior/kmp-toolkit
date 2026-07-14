package com.siddharth.kmp.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityPolicyTest {
    private fun audit(
        rooted: Boolean = false,
        emulator: Boolean = false,
        debugger: Boolean = false,
        hooked: Boolean = false,
        ssl: Boolean = false,
    ) = SecurityAudit(rooted, emulator, debugger, hooked, ssl, signals = emptyList())

    @Test
    fun cleanDevice_allows() {
        val d = SecurityPolicy.evaluate(audit(), SecurityPosture.strict())
        assertEquals(ThreatAction.ALLOW, d.action)
        assertFalse(d.shouldBlock)
        assertTrue(d.triggered.isEmpty())
    }

    @Test
    fun strict_blocksOnRoot() {
        val d = SecurityPolicy.evaluate(audit(rooted = true), SecurityPosture.strict())
        assertTrue(d.shouldBlock)
        assertEquals(Threat.ROOT to ThreatAction.BLOCK, d.triggered.single())
    }

    @Test
    fun strict_emulatorOnly_warnsNotBlocks() {
        val d = SecurityPolicy.evaluate(audit(emulator = true), SecurityPosture.strict())
        assertEquals(ThreatAction.WARN, d.action)
        assertFalse(d.shouldBlock)
    }

    @Test
    fun overallActionIsMostSevere() {
        // Emulator (WARN) + hook (BLOCK) → BLOCK wins.
        val d = SecurityPolicy.evaluate(audit(emulator = true, hooked = true), SecurityPosture.strict())
        assertEquals(ThreatAction.BLOCK, d.action)
        assertEquals(2, d.triggered.size)
    }

    @Test
    fun lenient_neverBlocks() {
        val d =
            SecurityPolicy.evaluate(
                audit(rooted = true, debugger = true, hooked = true, ssl = true),
                SecurityPosture.lenient(),
            )
        assertFalse(d.shouldBlock)
        assertEquals(ThreatAction.WARN, d.action) // root/hook/ssl → WARN, debugger → ALLOW
    }

    @Test
    fun bypassedAuditNeverTriggers() {
        // A VAPT build with bypass flags reports a clean audit → ALLOW even under strict posture.
        val d = SecurityPolicy.evaluate(audit(), SecurityPosture.strict())
        assertEquals(ThreatAction.ALLOW, d.action)
    }
}
