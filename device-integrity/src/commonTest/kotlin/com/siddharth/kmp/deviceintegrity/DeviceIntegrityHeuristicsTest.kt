package com.siddharth.kmp.deviceintegrity

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the pure heuristics — no device, no Robolectric. Exercises [isEmulatorBuild] and
 * [isRootTag] against known emulator and real-device `Build.*` values (ported from :security).
 */
class DeviceIntegrityHeuristicsTest {
    @Test
    fun android_emulator_sdk_gphone_is_flagged() {
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
    fun generic_goldfish_emulator_is_flagged() {
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
    fun genymotion_vbox_emulator_is_flagged() {
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
    fun real_pixel_device_is_not_flagged() {
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
    fun real_samsung_device_is_not_flagged() {
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
    fun test_keys_tag_is_a_root_signal() {
        assertTrue(isRootTag("test-keys"))
        assertTrue(isRootTag("release-keys,test-keys"))
    }

    @Test
    fun release_keys_tag_is_not_a_root_signal() {
        assertFalse(isRootTag("release-keys"))
    }

    @Test
    fun null_tags_is_not_a_root_signal() {
        assertFalse(isRootTag(null))
    }

    @Test
    fun known_su_paths_present_in_detection_list() {
        assertTrue(ROOT_BINARY_PATHS.contains("/system/xbin/su"))
        assertTrue(ROOT_BINARY_PATHS.any { it.contains("magisk") })
    }
}
