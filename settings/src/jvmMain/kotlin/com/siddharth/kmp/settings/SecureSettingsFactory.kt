package com.siddharth.kmp.settings

import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop/JVM secure settings: the whole properties store is AES-256-GCM encrypted at rest.
 *
 * The key is generated once and persisted base64 in a sibling `<store>.key` file (0600 where the FS
 * supports POSIX perms). ponytail: the key sits next to the ciphertext — a determined local attacker
 * with FS access can read both. Upgrade path when that threat matters: move the key to the OS
 * credential store (macOS Keychain / Windows DPAPI / Linux libsecret), same as the reference
 * template's deferred "Phase 4".
 */
actual class SecureSettingsFactory(
    private val storeFile: File = defaultStoreFile(),
) {
    private val keyFile: File get() = File(storeFile.parentFile, storeFile.name + ".key")

    actual fun create(): Settings {
        val key = loadOrCreateKey()
        val props = Properties()
        if (storeFile.exists() && storeFile.length() > 0) {
            ByteArrayInputStream(decrypt(key, storeFile.readBytes())).use { props.load(it) }
        }
        return PropertiesSettings(props) { updated ->
            val plain = ByteArrayOutputStream().use { bos ->
                updated.store(bos, null)
                bos.toByteArray()
            }
            storeFile.parentFile?.mkdirs()
            storeFile.writeBytes(encrypt(key, plain))
        }
    }

    private fun loadOrCreateKey(): SecretKeySpec {
        if (keyFile.exists() && keyFile.length() > 0) {
            return SecretKeySpec(Base64.getDecoder().decode(keyFile.readText().trim()), "AES")
        }
        val raw = KeyGenerator.getInstance("AES").apply { init(256, SecureRandom()) }
            .generateKey().encoded
        keyFile.parentFile?.mkdirs()
        keyFile.writeText(Base64.getEncoder().encodeToString(raw))
        runCatching {
            Files.setPosixFilePermissions(keyFile.toPath(), PosixFilePermissions.fromString("rw-------"))
        }
        return SecretKeySpec(raw, "AES")
    }

    private fun encrypt(key: SecretKeySpec, plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        return iv + cipher.doFinal(plain) // IV prepended, GCM tag appended by the cipher
    }

    private fun decrypt(key: SecretKeySpec, blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(blob.copyOfRange(IV_BYTES, blob.size))
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        private fun defaultStoreFile(): File =
            File(System.getProperty("user.home"), ".kmp-toolkit-secure/secure_settings.enc")
    }
}
