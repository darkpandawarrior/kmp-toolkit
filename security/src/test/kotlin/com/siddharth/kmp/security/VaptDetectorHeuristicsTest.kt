package com.siddharth.kmp.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure, parsable bits extracted from the VAPT detectors. These need no device
 * and no Robolectric — they exercise [parseTracerPid], [mapsIndicatesFrida] and
 * [isPermissiveTrustManagerClassName] against sample `/proc` and class-name inputs. The parts that
 * touch a real process (thread enumeration, socket probes, live TrustManager introspection) are
 * intentionally not unit-tested here.
 */
class VaptDetectorHeuristicsTest {
    // --- AntiDebugDetector.parseTracerPid --------------------------------------------------------

    @Test
    fun `TracerPid of zero means not traced`() {
        val status =
            """
            Name:	com.example.app
            State:	S (sleeping)
            Tgid:	9876
            Pid:	9876
            PPid:	1234
            TracerPid:	0
            Uid:	10123	10123	10123	10123
            """.trimIndent()
        assertEquals(0, parseTracerPid(status))
    }

    @Test
    fun `non-zero TracerPid is parsed as the tracing pid`() {
        val status =
            """
            Name:	com.example.app
            State:	t (tracing stop)
            TracerPid:	4571
            Uid:	10123	10123	10123	10123
            """.trimIndent()
        assertEquals(4571, parseTracerPid(status))
    }

    @Test
    fun `missing TracerPid line returns zero`() {
        val status =
            """
            Name:	com.example.app
            State:	S (sleeping)
            PPid:	1234
            """.trimIndent()
        assertEquals(0, parseTracerPid(status))
    }

    @Test
    fun `malformed TracerPid value returns zero`() {
        assertEquals(0, parseTracerPid("TracerPid:\tnot-a-number"))
    }

    @Test
    fun `empty status returns zero`() {
        assertEquals(0, parseTracerPid(""))
    }

    // --- AntiHookDetector.mapsIndicatesFrida -----------------------------------------------------

    @Test
    fun `clean maps has no frida markers`() {
        val maps =
            """
            700000000-700001000 r-xp 00000000 fd:00 12345 /system/lib64/libc.so
            700002000-700003000 r--p 00000000 fd:00 12346 /apex/com.android.art/lib64/libart.so
            700004000-700005000 rw-p 00000000 00:00 0 [anon:libc_malloc]
            """.trimIndent()
        assertFalse(mapsIndicatesFrida(maps))
    }

    @Test
    fun `frida-agent library in maps is detected`() {
        val maps =
            """
            700000000-700001000 r-xp 00000000 fd:00 12345 /system/lib64/libc.so
            7f0000000-7f0010000 r-xp 00000000 fd:03 99999 /data/local/tmp/re.frida.server/frida-agent-64.so
            """.trimIndent()
        assertTrue(mapsIndicatesFrida(maps))
    }

    @Test
    fun `libgadget and xposed markers are detected case-insensitively`() {
        assertTrue(mapsIndicatesFrida("7f00-7f10 r-xp 0 fd:03 1 /data/app/libGADGET.so"))
        assertTrue(mapsIndicatesFrida("7f00-7f10 r-xp 0 fd:03 1 /system/framework/XposedBridge.jar"))
        assertTrue(mapsIndicatesFrida("7f00-7f10 r-xp 0 fd:03 1 /data/app/libsubstrate.so"))
    }

    // --- AntiSslBypassDetector.isPermissiveTrustManagerClassName ---------------------------------

    @Test
    fun `legitimate conscrypt trust manager is not permissive`() {
        assertFalse(isPermissiveTrustManagerClassName("com.android.org.conscrypt.TrustManagerImpl"))
        assertFalse(isPermissiveTrustManagerClassName("sun.security.ssl.X509TrustManagerImpl"))
    }

    @Test
    fun `all-trusting trust manager names are flagged`() {
        assertTrue(isPermissiveTrustManagerClassName("com.example.TrustAllManager"))
        assertTrue(isPermissiveTrustManagerClassName("dev.asd.test.BypassSSLTrustManager"))
        assertTrue(isPermissiveTrustManagerClassName("com.x.NullTrustManager"))
        assertTrue(isPermissiveTrustManagerClassName("io.y.InsecureTrustManager"))
        assertTrue(isPermissiveTrustManagerClassName("z.AcceptAllCerts"))
        assertTrue(isPermissiveTrustManagerClassName("q.EmptyTrustManager"))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertTrue(isPermissiveTrustManagerClassName("com.foo.TRUSTALLMANAGER"))
    }
}
