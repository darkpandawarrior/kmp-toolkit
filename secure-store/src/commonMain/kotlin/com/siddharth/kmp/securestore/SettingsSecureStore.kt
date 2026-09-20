package com.siddharth.kmp.securestore

import com.russhwolf.settings.Settings

/** Key the availability probe writes and immediately deletes. Never holds anything meaningful. */
private const val PROBE_KEY = "com.siddharth.kmp.securestore.probe"
private const val PROBE_VALUE = "ok"

/**
 * Every line of [SecureStore] that is not "how do I build the platform's `Settings`" lives here, so
 * the two actuals are a constructor each and nothing else can drift between them.
 *
 * @param open opens the platform store. Called at most once, lazily — an `EncryptedSharedPreferences`
 *   on Android does Keystore work on first touch, and a library has no business doing that in a
 *   consumer's constructor.
 */
internal class SettingsSecureStore(
    private val open: () -> Settings,
) {
    /**
     * Opening is not enough to trust a store, so this round-trips a canary: write, read back, delete.
     * An Android Keystore with a corrupted keyset throws on open; a Keychain the app is not entitled
     * to write to accepts the call and stores nothing. Only the second half of this catches that one,
     * and it is the failure that loses a user's saved token without a single log line.
     */
    private val opened: Result<Settings> by lazy {
        runCatching {
            val settings = open()
            settings.putString(PROBE_KEY, PROBE_VALUE)
            val readBack = settings.getStringOrNull(PROBE_KEY)
            settings.remove(PROBE_KEY)
            check(readBack == PROBE_VALUE) {
                "the store accepted a write and did not return it (read back: ${if (readBack == null) "nothing" else "a different value"})"
            }
            settings
        }
    }

    fun status(): SecureStoreStatus =
        opened.fold(
            onSuccess = { SecureStoreStatus.Available },
            onFailure = { SecureStoreStatus.Unavailable(it.describe()) },
        )

    fun put(
        key: String,
        value: String,
    ): Boolean = withStore { it.putString(key, value) }

    fun get(key: String): String? = opened.getOrNull()?.getStringOrNull(key)

    fun remove(key: String): Boolean = withStore { it.remove(key) }

    fun contains(key: String): Boolean = opened.getOrNull()?.hasKey(key) ?: false

    fun clear(): Boolean = withStore { it.clear() }

    /**
     * Runs [block] against the store, reporting whether it actually happened. A write that throws
     * *after* a successful probe (the disk filled, the Keystore was invalidated by a fingerprint
     * being added mid-session) is a `false` too — the caller is told the same thing either way,
     * which is the only answer that keeps it honest.
     */
    private inline fun withStore(block: (Settings) -> Unit): Boolean {
        val settings = opened.getOrNull() ?: return false
        return runCatching { block(settings) }.isSuccess
    }
}

/**
 * A one-line, loggable description of a failure. Uses the message where there is one and the type
 * otherwise, because a bare `GeneralSecurityException` with a null message is a reason too — just a
 * worse one than nothing at all would be.
 */
internal fun Throwable.describe(): String {
    val detail = message?.takeIf { it.isNotBlank() } ?: this::class.simpleName ?: "unknown error"
    return "The encrypted store is unavailable: $detail"
}
