package com.siddharth.kmp.llmchat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [SecureKeyStore] itself has a different constructor per platform (Android needs a `Context`,
 * others don't), so it can't be built generically here — these tests cover the two pieces that
 * ARE pure common code: the provider->key-name mapping, and [loadAiProviderConfig] (which takes a
 * plain `(ProviderId) -> String?` precisely so it doesn't need a real platform store to test).
 */
class SecureKeyStoreTest {
    @Test
    fun cloudProvidersGetDistinctNonBlankStorageKeys() {
        val keys =
            listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.GEMINI)
                .map { it.secureStorageKeyOrNull() }
        assertEquals(3, keys.filterNotNull().distinct().size, "each cloud provider needs its own key: $keys")
    }

    @Test
    fun onDeviceAndOfflineFallbackHaveNoStorageKey() {
        assertNull(ProviderId.ON_DEVICE.secureStorageKeyOrNull())
        assertNull(ProviderId.OFFLINE_FALLBACK.secureStorageKeyOrNull())
    }

    @Test
    fun loadAiProviderConfigReadsOnlyTheThreeCloudKeys() {
        val saved =
            mapOf(
                ProviderId.ANTHROPIC.secureStorageKeyOrNull() to "anthropic-key",
                ProviderId.GEMINI.secureStorageKeyOrNull() to "gemini-key",
                // OpenAI: never saved.
            )
        val config =
            loadAiProviderConfig(
                getKey = { provider -> saved[provider.secureStorageKeyOrNull()] },
                selectedProvider = ProviderId.GEMINI,
                useOnDevice = true,
            )

        assertEquals("anthropic-key", config.anthropicKey)
        assertNull(config.openAiKey)
        assertEquals("gemini-key", config.geminiKey)
        assertEquals(ProviderId.GEMINI, config.selectedProvider)
        assertEquals(true, config.useOnDevice)
    }

    @Test
    fun loadAiProviderConfigDefaultsMatchAiProviderConfigDefaults() {
        val config = loadAiProviderConfig(getKey = { null })

        assertEquals(AiProviderConfig(), config)
    }
}
