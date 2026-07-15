package com.siddharth.kmp.deviceintegrity

// The web sandbox exposes no device-integrity signals — report a clean posture.
actual fun deviceIntegrity(config: DeviceIntegrityConfig): DeviceIntegrity =
    object : DeviceIntegrity {
        override fun inspect(): DeviceIntegrityReport =
            DeviceIntegrityReport(
                rooted = false,
                emulator = false,
                debuggerAttached = false,
                signals = listOf("device-integrity: web — no device signals available"),
            )
    }
