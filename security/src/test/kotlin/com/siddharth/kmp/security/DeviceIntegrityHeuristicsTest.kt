package com.siddharth.kmp.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure heuristics extracted from [AndroidDeviceIntegrity]. These need no
 * device, no Robolectric — they exercise [isEmulatorBuild] and [isRootTag] against known emulator
 * and real-device `Build.*` values.
 */
class DeviceIntegrityHeuristicsTest {
    @Test
    fun `Android emulator (sdk_gphone) is flagged`() {
        assertTrue(
            isEmulatorBuild(
                fingerprint = "google/sdk_gphone64_arm64/emu64a:14/UE1A.230829.036/11228894:user/release-keys",
                model = "sdk_gphone64_arm64",
                product = "sdk_gphone64_arm64",
                hardware = "ranchu",
                manufacturer = "Google",
                brand = "google",
                device = "emu64a",
            ),
        )
    }

    @Test
    fun `generic goldfish emulator is flagged`() {
        assertTrue(
            isEmulatorBuild(
                fingerprint = "generic/sdk/generic:8.1.0/OSM1.180201.023/eng.user:userdebug/test-keys",
                model = "Android SDK built for x86",
                product = "sdk",
                hardware = "goldfish",
                manufacturer = "unknown",
                brand = "generic",
                device = "generic",
            ),
        )
    }

    @Test
    fun `Genymotion vbox emulator is flagged`() {
        assertTrue(
            isEmulatorBuild(
                fingerprint = "generic/vbox86p/vbox86p:8.0.0/OPR6.170623.017/vbox86p:userdebug/test-keys",
                model = "Samsung Galaxy S9",
                product = "vbox86p",
                hardware = "vbox86",
                manufacturer = "Genymotion",
                brand = "generic",
                device = "vbox86p",
            ),
        )
    }

    @Test
    fun `real Pixel device is NOT flagged as emulator`() {
        assertFalse(
            isEmulatorBuild(
                fingerprint = "google/oriole/oriole:14/AP2A.240905.003/12231197:user/release-keys",
                model = "Pixel 6",
                product = "oriole",
                hardware = "oriole",
                manufacturer = "Google",
                brand = "google",
                device = "oriole",
            ),
        )
    }

    @Test
    fun `real Samsung device is NOT flagged as emulator`() {
        assertFalse(
            isEmulatorBuild(
                fingerprint = "samsung/o1sxx/o1s:13/TP1A.220624.014/S908EXXU2AVK1:user/release-keys",
                model = "SM-S908E",
                product = "o1sxx",
                hardware = "qcom",
                manufacturer = "samsung",
                brand = "samsung",
                device = "o1s",
            ),
        )
    }

    @Test
    fun `test-keys tag is detected as root signal`() {
        assertTrue(isRootTag("test-keys"))
        assertTrue(isRootTag("release-keys,test-keys"))
    }

    @Test
    fun `release-keys tag is not a root signal`() {
        assertFalse(isRootTag("release-keys"))
    }

    @Test
    fun `null tags is not a root signal`() {
        assertFalse(isRootTag(null))
    }

    @Test
    fun `known su binary paths are present in the detection list`() {
        assertTrue(ROOT_BINARY_PATHS.contains("/system/xbin/su"))
        assertTrue(ROOT_BINARY_PATHS.any { it.contains("magisk") })
    }
}
