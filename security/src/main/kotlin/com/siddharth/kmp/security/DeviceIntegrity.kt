package com.siddharth.kmp.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import com.siddharth.kmp.common.AppLog
import java.io.File

private const val TAG = "DeviceIntegrity"

/**
 * Runtime posture check for the device the app is running on. A payments app runs this on launch to
 * decide whether to proceed, warn, or refuse to load card entry on a compromised device.
 */
interface DeviceIntegrity {
    fun inspect(): SecurityReport
}

/**
 * Outcome of a [DeviceIntegrity.inspect] pass.
 *
 * @property signals human-readable trail of every positive signal, for logging / a debug screen.
 * @property inspected whether the detectors actually ran. False means the other flags carry no
 *   information and MUST NOT be read as "clean". Mirrors the field of the same name on
 *   :device-integrity's DeviceIntegrityReport.
 * @property isCompromised the gate a caller acts on. Emulator alone does NOT compromise (dev/QA run
 *   on emulators); root, an attached debugger, or an inspection that never ran do.
 */
data class SecurityReport(
    val rooted: Boolean,
    val emulator: Boolean,
    val debuggerAttached: Boolean,
    val signals: List<String>,
    val inspected: Boolean = true,
) {
    /**
     * **Fails closed.** An un-run inspection ([inspected] == false) reports compromised.
     *
     * This used to be `rooted || debuggerAttached`, with no [inspected] field to consult — so a
     * [SecurityReport] whose detectors had never run reported `isCompromised == false` and the app
     * proceeded. That is manufactured assurance: the worst property a root-detection surface can
     * have, because the caller cannot tell "we checked and it is clean" from "we never checked".
     *
     * :device-integrity, the KMP sibling of this module, names precisely this defect in its own
     * KDoc as its reason for existing and already fails closed. This one was left behind, and
     * :security is the module PaymentsLab-KMP substitutes — so the fix landed everywhere except the
     * place it was actually being used.
     */
    val isCompromised: Boolean
        get() = !inspected || rooted || debuggerAttached
}

/**
 * Policy knobs for how [AndroidDeviceIntegrity] and the [SecurityAuditor] interpret their raw
 * signals.
 *
 * ## Interpretation knobs
 * @property allowRootedDevice reserved for a caller that wants to *tolerate* root (kept in the
 *   report either way; this lets a caller downgrade root from blocking to informational).
 * @property allowEmulator emulators are allowed by default so CI/QA keeps working.
 * @property allowDebuggerInDebug when the app is itself a debuggable build, an attached debugger is
 *   expected (Android Studio) and is not treated as an attack signal.
 *
 * ## VAPT bypass flags
 * These follow a common VAPT-testing `BuildConfig.bypassRoot / bypassEmulator / bypassHook /
 * bypassSsl` toggle pattern. A VAPT (Vulnerability Assessment & Penetration Testing) test build often has to run on a
 * rooted / hooked / debuggable device on purpose so the auditor can exercise the app's flows without
 * the app hard-blocking itself. Each flag lets a specific category still be *detected and logged*
 * (so diagnostics stay accurate) while being excluded from the [SecurityAudit.isCompromised] gate.
 *
 * All default to `false` (full protection). The app sets them from `BuildConfig` for VAPT/compliance
 * test builds only — never in a real release. See the module KDoc for the exact `buildConfigField`
 * wiring the app should add.
 *
 * @property bypassRoot exclude a rooted-device signal from the compromise gate.
 * @property bypassEmulator exclude an emulator signal from the compromise gate (emulator is already
 *   non-blocking by default via [allowEmulator]; kept for naming symmetry with the other bypass flags).
 * @property bypassDebugger exclude an attached-debugger signal from the compromise gate.
 * @property bypassHook exclude Frida/Xposed/hook signals from the compromise gate.
 * @property bypassSsl exclude SSL-pinning-bypass signals from the compromise gate (for bank/PCI
 *   compliance testing where an interception proxy is deliberately in the path).
 *
 * ## UI-surface protection toggles (consumed by [AppSecurityManager])
 * These follow a common server-toggleable `enableScreenshotProtection / enableTapjackingProtection /
 * enableSecurityOverlay` flag pattern. Default `true` (protected); an app can flip one off per-client.
 *
 * @property screenshotProtectionEnabled gate for Activity-wide `FLAG_SECURE`.
 * @property tapjackingProtectionEnabled gate for `filterTouchesWhenObscured` + obscured-touch drop.
 * @property securityOverlayEnabled gate for re-asserting `FLAG_SECURE` on app background.
 */
data class SecurityConfig(
    val allowRootedDevice: Boolean = false,
    val allowEmulator: Boolean = true,
    val allowDebuggerInDebug: Boolean = true,
    // --- VAPT bypass flags (common VAPT-testing bypass toggle pattern) — default false. ---
    val bypassRoot: Boolean = false,
    val bypassEmulator: Boolean = false,
    val bypassDebugger: Boolean = false,
    val bypassHook: Boolean = false,
    val bypassSsl: Boolean = false,
    // --- UI-surface protection toggles (common server-toggleable enable* flag pattern) — default true. ---
    val screenshotProtectionEnabled: Boolean = true,
    val tapjackingProtectionEnabled: Boolean = true,
    val securityOverlayEnabled: Boolean = true,
)

/**
 * Real Android implementation. Each detector appends to `signals`, so a report doubles as an audit
 * trail. The device-independent decision logic lives in the top-level pure functions below
 * ([isEmulatorBuild], [isRootTag], [rootBinaryExists], …) so it is JVM-unit-testable without a
 * device or Robolectric.
 */
class AndroidDeviceIntegrity(
    private val context: Context,
    private val config: SecurityConfig,
) : DeviceIntegrity {
    override fun inspect(): SecurityReport {
        val signals = mutableListOf<String>()

        val rooted = detectRoot(signals)
        val emulator = detectEmulator(signals)
        val debuggerAttached = detectDebugger(signals)

        val report =
            SecurityReport(
                rooted = rooted,
                emulator = emulator,
                debuggerAttached = debuggerAttached,
                signals = signals.toList(),
                // Explicit rather than defaulted: this class holds a Context, so its detectors
                // always run, and saying so here is what makes the field meaningful anywhere else.
                inspected = true,
            )
        if (report.isCompromised) {
            AppLog.w("Device compromised: ${signals.joinToString()}", tag = TAG)
        } else {
            AppLog.i("Device integrity OK (emulator=$emulator)", tag = TAG)
        }
        return report
    }

    private fun detectRoot(signals: MutableList<String>): Boolean {
        var rooted = false

        val foundPath = ROOT_BINARY_PATHS.firstOrNull { path -> File(path).exists() }
        if (foundPath != null) {
            signals += "root: binary present at $foundPath"
            rooted = true
        }

        if (isRootTag(Build.TAGS)) {
            signals += "root: Build.TAGS contains test-keys"
            rooted = true
        }

        if (suOnPath()) {
            signals += "root: 'which su' resolved to an executable"
            rooted = true
        }

        val writableDir = WRITABLE_SYSTEM_DIRS.firstOrNull { dir -> File(dir).canWrite() }
        if (writableDir != null) {
            signals += "root: system dir is writable ($writableDir)"
            rooted = true
        }

        return rooted
    }

    private fun detectEmulator(signals: MutableList<String>): Boolean {
        val emulator =
            isEmulatorBuild(
                fingerprint = Build.FINGERPRINT,
                model = Build.MODEL,
                product = Build.PRODUCT,
                hardware = Build.HARDWARE,
                manufacturer = Build.MANUFACTURER,
                brand = Build.BRAND,
                device = Build.DEVICE,
            )
        if (emulator) {
            signals += "emulator: build heuristics matched (${Build.FINGERPRINT})"
            if (!config.allowEmulator) {
                signals += "emulator: disallowed by SecurityConfig"
            }
        }
        return emulator
    }

    private fun detectDebugger(signals: MutableList<String>): Boolean {
        val connected = Debug.isDebuggerConnected()
        val debuggableBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        // A debugger on a debuggable build is Android Studio — expected when allowed.
        if (debuggableBuild && config.allowDebuggerInDebug) {
            if (connected) {
                signals += "debugger: attached but tolerated on debuggable build"
            }
            return false
        }

        if (connected) {
            signals += "debugger: Debug.isDebuggerConnected() == true"
        }
        if (debuggableBuild) {
            signals += "debugger: app is FLAG_DEBUGGABLE"
        }
        return connected || debuggableBuild
    }

    /**
     * Best-effort `which su`. Any failure — no such binary, SELinux denial, a vendor ROM that
     * refuses the exec — means "su did not resolve", which is the good case. The breadth of the
     * catch is the point, so the variable is named `ignored` to say so on the page rather than in
     * a baseline file.
     */
    private fun suOnPath(): Boolean =
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val output = process.inputStream.bufferedReader().readLine()
            process.waitFor()
            !output.isNullOrBlank()
        } catch (ignored: Exception) {
            false
        }
}

// --- Pure, device-independent heuristics (unit-testable on the JVM) -----------------------------

/**
 * Common su / Magisk / SuperSU binary locations. Presence of any is a strong root signal.
 * Kept as top-level `internal` data so tests can assert against the same list the detector uses.
 */
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
