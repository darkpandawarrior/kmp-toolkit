package com.siddharth.kmp.llmchat

import com.siddharth.kmp.settings.SecureSettingsFactory

/**
 * Backed by `:settings`'s AES-256-GCM-encrypted `PropertiesSettings` file (default
 * `~/.kmp-toolkit-secure/secure_settings.enc`, key file beside it, `0600`).
 */
actual class SecureKeyStore {
    private val settings = SecureSettingsFactory().create()

    actual fun getKey(provider: ProviderId): String? = provider.secureStorageKeyOrNull()?.let(settings::getStringOrNull)

    actual fun setKey(
        provider: ProviderId,
        apiKey: String?,
    ) {
        val storageKey = provider.secureStorageKeyOrNull() ?: return
        if (apiKey.isNullOrBlank()) {
            settings.remove(storageKey)
        } else {
            settings.putString(storageKey, apiKey)
        }
    }
}
