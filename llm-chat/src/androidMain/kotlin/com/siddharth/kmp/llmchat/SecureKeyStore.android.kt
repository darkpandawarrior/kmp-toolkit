package com.siddharth.kmp.llmchat

import android.content.Context
import com.siddharth.kmp.settings.SecureSettingsFactory

/** Backed by `:settings`'s `EncryptedSharedPreferences` (`MasterKey.AES256_GCM`) store. */
actual class SecureKeyStore(context: Context) {
    private val settings = SecureSettingsFactory(context).create()

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
