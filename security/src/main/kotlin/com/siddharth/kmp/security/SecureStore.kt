package com.siddharth.kmp.security

/**
 * A minimal key/value store contract for secrets — payment-method tokens, session keys, PINs.
 *
 * Implementations decide the at-rest guarantee: [FakeSecureStore] is a plain in-memory map for
 * tests and Compose previews, while [KeystoreSecureStore] backs every value with AES-256-GCM keyed
 * by an Android Keystore key that never leaves the TEE/StrongBox.
 *
 * The interface is deliberately tiny (no `Flow`, no observers) so it can be depended on from any
 * layer without pulling in coroutines — a payments app reads a saved token synchronously at the
 * moment it needs to pre-fill a form, not reactively.
 */
interface SecureStore {
    fun putString(
        key: String,
        value: String,
    )

    fun getString(key: String): String?

    fun remove(key: String)

    fun contains(key: String): Boolean

    fun clear()
}

/**
 * In-memory [SecureStore] backed by a [MutableMap]. Holds no encryption and no persistence — it
 * exists so ViewModels, repositories, and Composable previews can be exercised on the JVM without a
 * real Android Keystore or device. Never use in production wiring.
 */
class FakeSecureStore(
    initial: Map<String, String> = emptyMap(),
) : SecureStore {
    private val backing: MutableMap<String, String> = initial.toMutableMap()

    override fun putString(
        key: String,
        value: String,
    ) {
        backing[key] = value
    }

    override fun getString(key: String): String? = backing[key]

    override fun remove(key: String) {
        backing.remove(key)
    }

    override fun contains(key: String): Boolean = backing.containsKey(key)

    override fun clear() {
        backing.clear()
    }
}
