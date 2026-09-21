package com.siddharth.kmp.securestore

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SecureStore]'s actuals are a constructor each; all the behaviour lives in [SettingsSecureStore],
 * which takes a `() -> Settings` precisely so it can be exercised here without a device Keystore or
 * Keychain.
 */
class SettingsSecureStoreTest {
    @Test
    fun aHealthyStoreReportsAvailableAndRoundTrips() {
        val store = SettingsSecureStore { MapSettings() }

        assertEquals(SecureStoreStatus.Available, store.status())
        assertTrue(store.put("token", "tok_live_42"))
        assertEquals("tok_live_42", store.get("token"))
        assertTrue(store.contains("token"))
        assertTrue(store.remove("token"))
        assertNull(store.get("token"))
    }

    @Test
    fun theProbeLeavesNothingBehind() {
        val backing = MapSettings()
        SettingsSecureStore { backing }.status()

        assertEquals(0, backing.keys.size, "the availability probe must not leave its canary in the store")
    }

    @Test
    fun aStoreThatCannotBeOpenedReportsWhyAndRefusesEveryWrite() {
        val store = SettingsSecureStore { error("keyset is corrupted") }

        val status = store.status()
        assertFalse(status.isAvailable)
        assertTrue("keyset is corrupted" in status.reason, "the reason must survive: ${status.reason}")

        assertFalse(store.put("token", "tok_live_42"), "an unavailable store must not claim a write landed")
        assertNull(store.get("token"))
        assertFalse(store.contains("token"))
        assertFalse(store.remove("token"))
        assertFalse(store.clear())
    }

    @Test
    fun aStoreThatSilentlyDropsWritesIsNotAvailable() {
        // The failure this whole class exists for: the backend accepts putString, returns normally,
        // and stores nothing. Opening it succeeds, so only the read-back half of the probe catches it.
        val store = SettingsSecureStore { WriteDroppingSettings() }

        assertFalse(store.status().isAvailable, "a store that drops writes must never report Available")
        assertFalse(store.put("token", "tok_live_42"))
    }
}

/** A `Settings` that accepts string writes and keeps none of them — a Keychain with no entitlement. */
private class WriteDroppingSettings(
    private val real: Settings = MapSettings(),
) : Settings by real {
    override fun putString(
        key: String,
        value: String,
    ) = Unit
}
