package com.siddharth.kmp.settings

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

actual class SecureSettingsFactory {
    @OptIn(ExperimentalSettingsImplementation::class)
    actual fun create(): Settings {
        return KeychainSettings(service = "com.siddharth.kmp.secure")
    }
}
