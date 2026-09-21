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
private val EMULATOR_FINGERPRINT_PREFIXES = listOf("generic", "unknown")

private val EMULATOR_FINGERPRINT_MARKERS = listOf("emulator", "sdk_gphone", "vbox")

private val EMULATOR_HARDWARE_MARKERS = listOf("goldfish", "ranchu", "vbox86", "ttvm_x86")

private val EMULATOR_MODEL_MARKERS =
    listOf("sdk_gphone", "emulator", "android sdk built for", "google_sdk")

private val EMULATOR_PRODUCT_MARKERS =
    listOf("sdk_gphone", "sdk_google", "google_sdk", "emulator", "vbox86")

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
    val hw = hardware.lowercase()
    val mdl = model.lowercase()
    val prod = product.lowercase()
    val mfr = manufacturer.lowercase()
    val brnd = brand.lowercase()
    val genericBrand = brnd.startsWith("generic")

    // One named predicate per Build.* field. Previously this was five stacked `if`s of four to six
    // `||` terms each, which read as one 22-branch function and hid the operator-precedence
    // subtlety on the manufacturer line: `a || b && c` is `a || (b && c)`, now parenthesised.
    val fingerprintSaysEmulator =
        EMULATOR_FINGERPRINT_PREFIXES.any { fp.startsWith(it) } ||
            EMULATOR_FINGERPRINT_MARKERS.any { fp.contains(it) }
    val hardwareSaysEmulator = EMULATOR_HARDWARE_MARKERS.any { hw.contains(it) }
    val modelSaysEmulator = EMULATOR_MODEL_MARKERS.any { mdl.contains(it) }
    val productSaysEmulator = EMULATOR_PRODUCT_MARKERS.any { prod.contains(it) } || prod.startsWith("sdk")
    val vendorSaysEmulator =
        mfr.contains("genymotion") ||
            (mfr.contains("unknown") && genericBrand) ||
            device.lowercase().contains("vbox86") ||
            genericBrand

    return fingerprintSaysEmulator ||
        hardwareSaysEmulator ||
        modelSaysEmulator ||
        productSaysEmulator ||
        vendorSaysEmulator
}
