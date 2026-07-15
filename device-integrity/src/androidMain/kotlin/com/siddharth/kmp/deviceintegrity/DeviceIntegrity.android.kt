package com.siddharth.kmp.deviceintegrity

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import java.io.File

// The Android actual needs a Context to read applicationInfo flags; the common factory carries no
// Context (KMP shape), so the host app installs the application Context once at startup. Without it,
// inspection degrades to a clean report rather than crashing (e.g. JVM unit tests on this source set).
private var appContextHolder: Context? = null

/** Install the application [Context] used by the Android device-integrity actual. Call once at startup. */
fun installDeviceIntegrityContext(context: Context) {
    appContextHolder = context.applicationContext
}

actual fun deviceIntegrity(config: DeviceIntegrityConfig): DeviceIntegrity =
    appContextHolder?.let { AndroidDeviceIntegrity(it, config) } ?: CleanDeviceIntegrity

private object CleanDeviceIntegrity : DeviceIntegrity {
    override fun inspect(): DeviceIntegrityReport =
        DeviceIntegrityReport(
            rooted = false,
            emulator = false,
            debuggerAttached = false,
            signals = listOf("device-integrity: no Context installed — reporting clean"),
        )
}

/**
 * Real Android implementation. Each detector appends to `signals`, so a report doubles as an audit
 * trail. The device-independent decision logic lives in the shared pure heuristics
 * ([isEmulatorBuild], [isRootTag], [ROOT_BINARY_PATHS], …) so it is JVM-unit-testable without a device.
 */
internal class AndroidDeviceIntegrity(
    private val context: Context,
    private val config: DeviceIntegrityConfig,
) : DeviceIntegrity {
    override fun inspect(): DeviceIntegrityReport {
        val signals = mutableListOf<String>()
        return DeviceIntegrityReport(
            rooted = detectRoot(signals),
            emulator = detectEmulator(signals),
            debuggerAttached = detectDebugger(signals),
            signals = signals.toList(),
        )
    }

    private fun detectRoot(signals: MutableList<String>): Boolean {
        var rooted = false

        ROOT_BINARY_PATHS.firstOrNull { File(it).exists() }?.let {
            signals += "root: binary present at $it"
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
        WRITABLE_SYSTEM_DIRS.firstOrNull { File(it).canWrite() }?.let {
            signals += "root: system dir is writable ($it)"
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
            if (!config.allowEmulator) signals += "emulator: disallowed by config"
        }
        return emulator
    }

    private fun detectDebugger(signals: MutableList<String>): Boolean {
        val connected = Debug.isDebuggerConnected()
        val debuggableBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        // A debugger on a debuggable build is the IDE — expected when allowed.
        if (debuggableBuild && config.allowDebuggerInDebug) {
            if (connected) signals += "debugger: attached but tolerated on debuggable build"
            return false
        }
        if (connected) signals += "debugger: Debug.isDebuggerConnected() == true"
        if (debuggableBuild) signals += "debugger: app is FLAG_DEBUGGABLE"
        return connected || debuggableBuild
    }

    /** Best-effort `which su` — an [Exception] means the binary is absent, which is the good case. */
    private fun suOnPath(): Boolean =
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val output = process.inputStream.bufferedReader().readLine()
            process.waitFor()
            !output.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
}
