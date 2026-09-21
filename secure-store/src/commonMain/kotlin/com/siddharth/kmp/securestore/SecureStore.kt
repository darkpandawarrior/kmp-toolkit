package com.siddharth.kmp.securestore

/**
 * Whether this device can actually keep a secret at rest *right now*, and if not, why.
 *
 * The capability flag for [SecureStore], and — like `BiometricAvailability` — deliberately not a
 * `Boolean`. An encrypted store fails for reasons a caller can act on: a corrupted Keystore keyset
 * survives an app-data clear, a Keychain refuses writes to an app with no keychain-sharing
 * entitlement, a device with a broken TEE never recovers. "false" tells a payments flow nothing
 * about whether to retry, re-provision, or refuse to store the token at all.
 *
 * Every state carries a [reason] that is safe to log. It never contains a stored value or key name.
 */
sealed interface SecureStoreStatus {
    /** Plain-English explanation of this state. Never blank. */
    val reason: String

    /** True only for [Available]; a convenience for `if`, not a replacement for [reason]. */
    val isAvailable: Boolean get() = this is Available

    /** The store was opened and a write/read-back/delete round-trip succeeded. */
    data object Available : SecureStoreStatus {
        override val reason: String = "The encrypted store is open and verified."
    }

    /** The store could not be opened, or did not return what it was given. [reason] carries the detail. */
    data class Unavailable(
        override val reason: String,
    ) : SecureStoreStatus
}

/**
 * A small encrypted key/value store for secrets — session tokens, payment-method handles, PINs —
 * with one API across Android and iOS.
 *
 * ### What backs it
 * - **Android** — `EncryptedSharedPreferences` under an Android Keystore `MasterKey` (AES-256-GCM).
 * - **iOS** — the Keychain (`KeychainSettings`, service `com.siddharth.kmp.secure`).
 *
 * Both come from `:settings`' `SecureSettingsFactory`, which already owns that crypto. This module
 * is the cross-platform door onto it, not a second implementation of it.
 *
 * ### How this differs from `:security`'s `KeystoreSecureStore`
 * `:security` stays Android-only on purpose — it is the VAPT surface (hook/SSL-bypass/root
 * detection, `FLAG_SECURE`, certificate pinning), and its `KeystoreSecureStore` additionally hashes
 * *key names* so even the names of stored secrets never hit disk. Reach for that one when the threat
 * model is a rooted device with a filesystem dump. Reach for this one when the requirement is "the
 * same secret, on both platforms, encrypted at rest".
 *
 * ### Writes report, they do not pretend
 * Mutators return `Boolean` rather than `Unit`. A store that cannot be opened and silently accepts
 * a payment token is the `shareText` no-op with money attached: the caller believes the token is
 * saved, the next launch disagrees, and nothing anywhere said why. `false` means *not stored* —
 * call [isAvailable] for the reason.
 *
 * Construction is per-platform (Android needs a `Context`), which is the whole point of the
 * expect/actual seam; there is no common constructor to call.
 */
expect class SecureStore {
    /**
     * Why this store can or cannot hold a secret right now. See [SecureStoreStatus].
     *
     * The first call opens the store and round-trips a throwaway canary value through it, so a
     * backend that accepts writes and drops them is reported as unavailable rather than trusted.
     * The result is cached for the lifetime of this instance.
     */
    fun isAvailable(): SecureStoreStatus

    /** Stores [value] under [key]. Returns `false` if the store is unavailable and nothing was written. */
    fun putString(
        key: String,
        value: String,
    ): Boolean

    /** The value stored under [key], or `null` if absent — or if the store is unavailable. */
    fun getString(key: String): String?

    /** Deletes [key]. Returns `false` if the store is unavailable and nothing was deleted. */
    fun remove(key: String): Boolean

    /** Whether [key] holds a value. `false` when the store is unavailable. */
    fun contains(key: String): Boolean

    /** Deletes everything in this store. Returns `false` if the store is unavailable. */
    fun clear(): Boolean
}
