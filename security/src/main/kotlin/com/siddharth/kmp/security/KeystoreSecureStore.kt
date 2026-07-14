package com.siddharth.kmp.security

import android.content.Context
import android.content.SharedPreferences
import com.siddharth.kmp.common.AppLog
import java.security.MessageDigest

private const val TAG = "KeystoreSecureStore"

/**
 * A [SecureStore] that persists Keystore-encrypted values in a private [SharedPreferences] file.
 *
 * Two layers of obfuscation:
 * 1. **Values** are AES-256-GCM ciphertext produced by [KeystoreCrypto] — the key is hardware-backed
 *    and non-exportable, so the on-disk `xml` is inert without the device's TEE.
 * 2. **Keys** are stored as their SHA-256 hex digest, so even the *names* of stored secrets
 *    (e.g. `"razorpay_saved_card_token"`) never appear in plaintext on disk.
 *
 * This is the concrete at-rest story for a payments app: a saved payment-method token written here
 * is unreadable off-device even with root + a filesystem dump, because the AES key lives in the
 * Trusted Execution Environment / StrongBox and never touches the app's address space in the clear.
 *
 * @param prefsName name of the private prefs file; defaults to `kmp_secure`.
 */
class KeystoreSecureStore(
    context: Context,
    prefsName: String = "kmp_secure",
    private val crypto: KeystoreCrypto = KeystoreCrypto(),
) : SecureStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun putString(
        key: String,
        value: String,
    ) {
        val ciphertext = crypto.encrypt(value)
        if (ciphertext == null) {
            AppLog.e("putString: encryption failed for a value; not persisting", tag = TAG)
            return
        }
        prefs.edit().putString(hashKey(key), ciphertext).apply()
    }

    override fun getString(key: String): String? {
        val stored = prefs.getString(hashKey(key), null) ?: return null
        return crypto.decrypt(stored)
    }

    override fun remove(key: String) {
        prefs.edit().remove(hashKey(key)).apply()
    }

    override fun contains(key: String): Boolean = prefs.contains(hashKey(key))

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun hashKey(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
