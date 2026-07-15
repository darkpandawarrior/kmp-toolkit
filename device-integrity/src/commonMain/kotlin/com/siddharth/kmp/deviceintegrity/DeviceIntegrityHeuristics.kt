package com.siddharth.kmp.deviceintegrity

// Pure, device-independent heuristics — JVM-unit-testable without a device or Robolectric. The
// Android actual applies these against real Build.*/filesystem state. Kept as top-level `internal`
// data so tests assert against the same lists the detector uses.

/** Common su / Magisk / SuperSU binary locations. Presence of any is a strong root signal. */
internal val ROOT_BINARY_PATHS: List<String> =
    listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/system/sd/xbin/su",
        "/system/app/Superuser.apk",
        "/system/app/SuperSU.apk",
        "/data/adb/magisk",
        "/data/adb/magisk.db",
        "/data/adb/modules",
        "/system/xbin/busybox",
    )

/** System directories that are read-only on a stock device; writability implies a rooted /system. */
internal val WRITABLE_SYSTEM_DIRS: List<String> =
    listOf(
        "/system",
        "/system/bin",
        "/system/sbin",
        "/system/xbin",
        "/vendor/bin",
        "/sbin",
        "/etc",
    )

/** True when the build was signed with test (non-release) keys — the classic AOSP/rooted signal. */
internal fun isRootTag(tags: String?): Boolean = tags != null && tags.contains("test-keys")

/**
 * Pure emulator heuristic over `Build.*` values. Extracted so the exact matching rules can be
 * unit-tested against known emulator and real-device fingerprints without a device.
 */
internal fun isEmulatorBuild(
    fingerprint: String,
    model: String,
    product: String,
    hardware: String,
    manufacturer: String,
    brand: String = "",
    device: String = "",
): Boolean {
    val fp = fingerprint.lowercase()
    if (fp.startsWith("generic") ||
        fp.startsWith("unknown") ||
        fp.contains("emulator") ||
        fp.contains("sdk_gphone") ||
        fp.contains("vbox")
    ) {
        return true
    }

    val hw = hardware.lowercase()
    if (hw in setOf("goldfish", "ranchu", "vbox86", "ttvm_x86") ||
        hw.contains("goldfish") ||
        hw.contains("ranchu")
    ) {
        return true
    }

    val mdl = model.lowercase()
    if (mdl.contains("sdk_gphone") ||
        mdl.contains("emulator") ||
        mdl.contains("android sdk built for") ||
        mdl.contains("google_sdk")
    ) {
        return true
    }

    val prod = product.lowercase()
    if (prod.contains("sdk_gphone") ||
        prod.contains("sdk_google") ||
        prod == "google_sdk" ||
        prod.contains("emulator") ||
        prod.contains("vbox86") ||
        prod.startsWith("sdk")
    ) {
        return true
    }

    val mfr = manufacturer.lowercase()
    if (mfr.contains("genymotion") || mfr.contains("unknown") && brand.lowercase().startsWith("generic")) {
        return true
    }

    if (device.lowercase().contains("vbox86") || brand.lowercase().startsWith("generic")) {
        return true
    }

    return false
}
