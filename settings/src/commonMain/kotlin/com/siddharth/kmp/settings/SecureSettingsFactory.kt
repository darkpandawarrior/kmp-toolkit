package com.siddharth.kmp.settings

import com.russhwolf.settings.Settings

/**
 * Platform-specific factory that creates an encrypted [Settings] instance.
 *
 * Returns the standard multiplatform-settings [Settings] interface — zero API change for consumers.
 * - Android: EncryptedSharedPreferences (MasterKey AES256_GCM) -> SharedPreferencesSettings
 * - iOS:     KeychainSettings (service = "com.siddharth.kmp.secure")
 * - JVM:     AES-256-GCM-encrypted PropertiesSettings (key file beside the store, 0600)
 *
 * Construction is per-platform (the Android actual needs a Context, the JVM actual takes an
 * optional store File) — that is the whole point of the expect/actual factory.
 */
expect class SecureSettingsFactory {
    fun create(): Settings
}
