package com.siddharth.kmp.deviceintegrity

/**
 * Runtime posture check for the device the app is running on. A security-sensitive app runs this on
 * launch to decide whether to proceed, warn, or refuse to load sensitive UI on a compromised device.
 *
 * Portable across platforms: Android inspects root/emulator/debugger signals; iOS inspects
 * jailbreak/simulator signals; desktop (JVM) and web (wasm) are outside the mobile threat model and
 * report a clean posture (JVM additionally surfaces an attached JDWP debugger).
 *
 * This is the KMP sibling of :security's Android-only DeviceIntegrity — extracted so the non-Android
 * targets of a KMP app get the same seam. (:security stays Android-only for its VAPT/Keystore surface.)
 */
interface DeviceIntegrity {
    fun inspect(): DeviceIntegrityReport
}

/**
 * Outcome of a [DeviceIntegrity.inspect] pass.
 *
 * @property rooted device is rooted (Android) or jailbroken (iOS).
 * @property emulator running on an emulator (Android) or simulator (iOS). Non-blocking by default —
 *   dev/QA run on emulators.
 * @property debuggerAttached a debugger is attached and not tolerated by config.
 * @property signals human-readable trail of every positive signal, for logging / a debug screen.
 * @property inspected whether the platform was actually able to run its detectors. False means the
 *   checks did not run — on Android, that the application `Context` was never installed. The other
 *   flags are then meaningless and MUST NOT be read as "clean".
 */
data class DeviceIntegrityReport(
    val rooted: Boolean,
    val emulator: Boolean,
    val debuggerAttached: Boolean,
    val signals: List<String>,
    val inspected: Boolean = true,
) {
    /**
     * The default gate a caller acts on. Emulator alone does NOT compromise; root/jailbreak or a
     * (non-tolerated) debugger do. A caller that wants to *tolerate* root reads [rooted] directly.
     *
     * **Fails closed.** An un-run inspection ([inspected] == false) reports compromised. A security
     * check that cannot run must not answer "safe": before this, an Android app that forgot
     * `installDeviceIntegrityContext(context)` silently received rooted=false / emulator=false /
     * debuggerAttached=false and would happily proceed on a rooted device. The misconfiguration was
     * recorded only as a string inside [signals], which nothing was obliged to read. Failing open is
     * the worst property a root-detection library can have — it manufactures assurance it never
     * earned.
     */
    val isCompromised: Boolean
        get() = !inspected || rooted || debuggerAttached
}

/**
 * How [DeviceIntegrity] interprets its raw signals.
 *
 * @property allowEmulator emulators/simulators are allowed by default so CI/QA keeps working (they
 *   never gate; this only affects the logged signal).
 * @property allowDebuggerInDebug on a debuggable build an attached debugger is the IDE and is not
 *   treated as an attack signal.
 */
data class DeviceIntegrityConfig(
    val allowEmulator: Boolean = true,
    val allowDebuggerInDebug: Boolean = true,
)

/**
 * Platform factory for [DeviceIntegrity].
 *
 * On Android the actual needs an application `Context`, installed once at startup via
 * `installDeviceIntegrityContext(context)`. Without it the detectors cannot run, and the returned
 * report carries `inspected = false` — which [DeviceIntegrityReport.isCompromised] treats as
 * compromised. Install the Context; do not rely on the degraded path.
 */
expect fun deviceIntegrity(config: DeviceIntegrityConfig = DeviceIntegrityConfig()): DeviceIntegrity
