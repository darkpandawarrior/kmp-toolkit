package com.siddharth.kmp.securestore

import com.siddharth.kmp.settings.SecureSettingsFactory

/**
 * iOS actual — the Keychain, by way of `:settings`' `KeychainSettings` (service
 * `com.siddharth.kmp.secure`), the same store `:llm-chat`'s `SecureKeyStore` already writes to.
 *
 * No second Keychain wrapper: one `kSecAttrService` for the whole toolkit means a secret written
 * through one module is readable through another, and there is exactly one place to change if that
 * service name ever has to move.
 */
actual class SecureStore {
    private val delegate = SettingsSecureStore { SecureSettingsFactory().create() }

    actual fun isAvailable(): SecureStoreStatus = delegate.status()

    actual fun putString(
        key: String,
        value: String,
    ): Boolean = delegate.put(key, value)

    actual fun getString(key: String): String? = delegate.get(key)

    actual fun remove(key: String): Boolean = delegate.remove(key)

    actual fun contains(key: String): Boolean = delegate.contains(key)

    actual fun clear(): Boolean = delegate.clear()
}
