package com.siddharth.kmp.deviceintegrity

import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo

// iOS jailbreak/simulator posture. Jailbreak is the primary iOS signal (a jailbroken device exposes
// tell-tale binaries/paths outside the app sandbox). Debugger detection via sysctl(P_TRACED) needs
// raw C interop and isn't wired here — jailbreak covers the compromise gate.
actual fun deviceIntegrity(config: DeviceIntegrityConfig): DeviceIntegrity = IosDeviceIntegrity(config)

internal class IosDeviceIntegrity(
    @Suppress("unused") private val config: DeviceIntegrityConfig,
) : DeviceIntegrity {
    override fun inspect(): DeviceIntegrityReport {
        val signals = mutableListOf<String>()
        val jailbroken = detectJailbreak(signals)
        val simulator = detectSimulator(signals)
        return DeviceIntegrityReport(
            rooted = jailbroken,
            emulator = simulator,
            debuggerAttached = false,
            signals = signals.toList(),
        )
    }

    private fun detectJailbreak(signals: MutableList<String>): Boolean {
        val fm = NSFileManager.defaultManager
        val found = JAILBREAK_PATHS.firstOrNull { fm.fileExistsAtPath(it) }
        if (found != null) {
            signals += "jailbreak: path present at $found"
            return true
        }
        return false
    }

    private fun detectSimulator(signals: MutableList<String>): Boolean {
        val env = NSProcessInfo.processInfo.environment
        val simulator = env["SIMULATOR_DEVICE_NAME"] != null || env["SIMULATOR_UDID"] != null
        if (simulator) signals += "simulator: SIMULATOR_* environment present"
        return simulator
    }

    private companion object {
        /** Tell-tale jailbreak binaries/paths that don't exist on a stock, sandboxed iOS install. */
        val JAILBREAK_PATHS =
            listOf(
                "/Applications/Cydia.app",
                "/Applications/Sileo.app",
                "/Library/MobileSubstrate/MobileSubstrate.dylib",
                "/bin/bash",
                "/usr/sbin/sshd",
                "/etc/apt",
                "/private/var/lib/apt/",
                "/usr/bin/ssh",
                "/var/jb",
            )
    }
}
