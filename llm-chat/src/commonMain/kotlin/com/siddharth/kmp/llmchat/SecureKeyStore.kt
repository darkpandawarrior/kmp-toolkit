package com.siddharth.kmp.llmchat

/**
 * Where a BYOK provider API key persists between app launches.
 *
 * Before this, every consumer of [AiProviderConfig] had to invent its own storage for the key a
 * user pastes in — a plain `SharedPreferences` string, an in-memory field that forgets on restart,
 * or nothing at all. [SecureKeyStore] gives every platform one real, at-rest-encrypted place for it
 * (except `wasmJs`, see that actual's own caveat), so `loadAiProviderConfig` below can turn "what's
 * saved" into an [AiProviderConfig] without the app owning any storage code of its own.
 *
 * Android/iOS/JVM delegate to `:settings`'s `SecureSettingsFactory` — EncryptedSharedPreferences,
 * Keychain, and an AES-256-GCM-encrypted properties file respectively; that module already carries
 * the crypto, this class only adds the provider-key vocabulary on top of its generic `Settings`.
 */
expect class SecureKeyStore {
    /** The persisted key for [provider], or `null` if none was ever saved. */
    fun getKey(provider: ProviderId): String?

    /** Saves [apiKey] for [provider], or clears it when [apiKey] is `null`/blank. */
    fun setKey(
        provider: ProviderId,
        apiKey: String?,
    )
}

/**
 * Storage key each [ProviderId] is saved under, or `null` for [ProviderId.ON_DEVICE]/
 * [ProviderId.OFFLINE_FALLBACK] — neither names a stored secret, so [SecureKeyStore.getKey]/
 * [SecureKeyStore.setKey] treat them as a no-op read/write instead of crashing a caller that passes
 * one through generically. One mapping shared by every actual so a key name can't drift between
 * platforms; `when` stays exhaustive so a new [ProviderId] forces a decision here.
 */
internal fun ProviderId.secureStorageKeyOrNull(): String? =
    when (this) {
        ProviderId.ANTHROPIC -> "llm_chat.api_key.anthropic"
        ProviderId.OPENAI -> "llm_chat.api_key.openai"
        ProviderId.GEMINI -> "llm_chat.api_key.gemini"
        ProviderId.ON_DEVICE, ProviderId.OFFLINE_FALLBACK -> null
    }

/**
 * Builds an [AiProviderConfig] from whatever [getKey] returns for each cloud provider — the read
 * side of [SecureKeyStore.setKey], so a settings screen's "save key" action and
 * [buildProviderChain]'s "read keys" side stay in sync without the app gluing them together itself.
 * Takes a plain function rather than a [SecureKeyStore] so this stays testable with a fake map in
 * `commonTest` without needing a real platform store; pass `store::getKey` at the call site.
 *
 * [selectedProvider] and [useOnDevice] aren't secrets (no reason to encrypt "which provider is
 * picked"), so the caller passes them through from wherever its own app settings already keep
 * them; this only owns the part that actually needs at-rest protection.
 */
fun loadAiProviderConfig(
    getKey: (ProviderId) -> String?,
    selectedProvider: ProviderId = ProviderId.OFFLINE_FALLBACK,
    useOnDevice: Boolean = false,
): AiProviderConfig =
    AiProviderConfig(
        selectedProvider = selectedProvider,
        anthropicKey = getKey(ProviderId.ANTHROPIC),
        openAiKey = getKey(ProviderId.OPENAI),
        geminiKey = getKey(ProviderId.GEMINI),
        useOnDevice = useOnDevice,
    )
