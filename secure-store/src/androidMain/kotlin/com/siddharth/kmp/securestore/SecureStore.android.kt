package com.siddharth.kmp.securestore

import android.content.Context
import com.siddharth.kmp.settings.SecureSettingsFactory

/**
 * Android actual — `EncryptedSharedPreferences` keyed by an Android Keystore `MasterKey`
 * (AES-256-GCM), by way of `:settings`' `SecureSettingsFactory`.
 *
 * Takes an application [Context]; anything else would outlive its owner.
 */
actual class SecureStore(context: Context) {
    private val delegate =
        SettingsSecureStore { SecureSettingsFactory(context.applicationContext).create() }

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
