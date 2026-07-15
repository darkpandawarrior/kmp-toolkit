package com.siddharth.kmp.deviceintegrity

import java.lang.management.ManagementFactory

// Desktop/JVM is outside the mobile root/jailbreak threat model — report a clean posture, but surface
// an attached JDWP debugger (which config can tolerate, as desktop dev runs one routinely).
actual fun deviceIntegrity(config: DeviceIntegrityConfig): DeviceIntegrity = JvmDeviceIntegrity(config)

internal class JvmDeviceIntegrity(
    private val config: DeviceIntegrityConfig,
) : DeviceIntegrity {
    override fun inspect(): DeviceIntegrityReport {
        val signals = mutableListOf<String>()
        val jdwp =
            runCatching {
                ManagementFactory.getRuntimeMXBean().inputArguments.any {
                    it.contains("-agentlib:jdwp") || it.contains("-Xrunjdwp")
                }
            }.getOrDefault(false)
        if (jdwp) signals += "debugger: JDWP agent on the JVM command line"

        val debuggerAttached = jdwp && !config.allowDebuggerInDebug
        return DeviceIntegrityReport(
            rooted = false,
            emulator = false,
            debuggerAttached = debuggerAttached,
            signals = signals.toList(),
        )
    }
}
