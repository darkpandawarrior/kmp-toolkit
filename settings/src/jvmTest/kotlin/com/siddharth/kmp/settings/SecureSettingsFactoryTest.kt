package com.siddharth.kmp.settings

import com.russhwolf.settings.set
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureSettingsFactoryTest {
    private val dir: File = Files.createTempDirectory("kmp-settings-test").toFile()
    private val store = File(dir, "secure_settings.enc")

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun writeThenReadRoundTripsAcrossFactoryInstances() {
        SecureSettingsFactory(store).create().apply {
            this["token"] = "s3cr3t"
            this["count"] = 42
        }
        // Fresh instance = re-read + decrypt from disk, not the in-memory delegate.
        val reread = SecureSettingsFactory(store).create()
        assertEquals("s3cr3t", reread.getStringOrNull("token"))
        assertEquals(42, reread.getIntOrNull("count"))
    }

    @Test
    fun storeIsEncryptedAtRest() {
        SecureSettingsFactory(store).create()["token"] = "plaintext-canary"
        assertTrue(store.exists() && store.length() > 0, "store file should be written")
        val onDisk = store.readText(Charsets.ISO_8859_1)
        assertTrue("plaintext-canary" !in onDisk, "value must not be readable in plaintext on disk")
    }

    @Test
    fun missingKeyReturnsNull() {
        assertNull(SecureSettingsFactory(store).create().getStringOrNull("nope"))
    }
}
