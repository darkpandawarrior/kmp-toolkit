package com.siddharth.kmp.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.siddharth.kmp.common.AppLog
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "KeystoreCrypto"

/**
 * AES-256-GCM encryption anchored to a hardware-backed key in the [AndroidKeyStore].
 *
 * ## Why this over EncryptedSharedPreferences
 * `EncryptedSharedPreferences` (Jetpack Security) is now deprecated and hides the crypto behind a
 * `MasterKey` abstraction. For a security *showcase* we want the mechanics visible: an explicit
 * AES-256 key generated in the Keystore, an explicit GCM cipher, an explicit IV prepended to the
 * ciphertext. This makes the at-rest story auditable in one file.
 *
 * ## The key never leaves the TEE
 * The [SecretKey] returned by the Keystore is a non-exportable handle — the raw key material lives
 * in the Trusted Execution Environment (or a StrongBox secure element on devices that ship one).
 * We only ever hand plaintext/ciphertext *through* the cipher; the bytes of the key itself are
 * never present in app memory, so a heap dump of a compromised process yields nothing usable.
 *
 * ## Wire format
 * `Base64( IV(12 bytes) || ciphertext+GCMtag )`. GCM's 128-bit auth tag is appended to the
 * ciphertext by the provider, so [decrypt] both decrypts and verifies integrity — a tampered blob
 * fails the tag check and returns `null`.
 */
class KeystoreCrypto {
    fun encrypt(plaintext: String): String? =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

            val iv = cipher.iv // 12-byte GCM nonce chosen by the provider
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            AppLog.e("encrypt failed", e, tag = TAG)
            null
        }

    fun decrypt(encoded: String): String? {
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH_BYTES) {
                AppLog.w("decrypt: payload too short to contain IV + ciphertext", tag = TAG)
                return null
            }

            val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
            )

            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            // GCM tag mismatch (tampering), corrupt Base64, or a rotated/absent key all land here.
            AppLog.e("decrypt failed", e, tag = TAG)
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER,
            )
        keyGenerator.init(
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // No user-auth binding here — a saved token must decrypt on cold start without a
                // biometric prompt. Bind to auth (setUserAuthenticationRequired) for the PIN/card
                // CVV case instead.
                .build(),
        )
        return keyGenerator.generateKey()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "kmp_secure_store"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
    }
}
