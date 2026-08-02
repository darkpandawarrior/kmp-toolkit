package com.siddharth.kmp.auth

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenStoreTest {
    private val key = "refresh_token"

    @Test
    fun persistedRoundTrips() {
        val store = TokenStore(MapSettings(), key)
        assertNull(store.persisted(), "a fresh store holds nothing")
        store.setPersisted("r1")
        assertEquals("r1", store.persisted())
    }

    @Test
    fun ephemeralIsNeverWrittenToSettings() {
        // The whole point of the two-slot split: an access token must not reach disk.
        val settings = MapSettings()
        val store = TokenStore(settings, key)
        store.setEphemeral("access-token")
        assertEquals("access-token", store.ephemeral.value)
        assertEquals(0, settings.keys.size, "ephemeral leaked into the backing store")
    }

    @Test
    fun ephemeralDoesNotSurviveANewInstance() {
        val settings = MapSettings()
        TokenStore(settings, key).apply {
            setEphemeral("access-token")
            setPersisted("r1")
        }
        // Same backing store, new instance — stands in for process death.
        val reborn = TokenStore(settings, key)
        assertNull(reborn.ephemeral.value, "ephemeral must not survive")
        assertEquals("r1", reborn.persisted(), "persisted must survive")
    }

    @Test
    fun clearWipesBothSlots() {
        val settings = MapSettings()
        val store = TokenStore(settings, key)
        store.setEphemeral("a")
        store.setPersisted("r")
        store.clear()
        assertNull(store.ephemeral.value)
        assertNull(store.persisted())
        assertEquals(0, settings.keys.size)
    }

    @Test
    fun twoStoresOnOneSettingsDoNotCollideAcrossKeys() {
        // An app may hold more than one secret; distinct keys must stay independent.
        val settings = MapSettings()
        val a = TokenStore(settings, "token_a")
        val b = TokenStore(settings, "token_b")
        a.setPersisted("A")
        b.setPersisted("B")
        assertEquals("A", a.persisted())
        assertEquals("B", b.persisted())
        a.clear()
        assertNull(a.persisted())
        assertEquals("B", b.persisted(), "clearing one key must not wipe the other")
    }

    @Test
    fun ephemeralIsObservable() {
        val store = TokenStore(MapSettings(), key)
        val seen = mutableListOf<String?>()
        // StateFlow always replays its current value, so the initial null is expected first.
        seen += store.ephemeral.value
        store.setEphemeral("a")
        seen += store.ephemeral.value
        store.clear()
        seen += store.ephemeral.value
        assertEquals(listOf(null, "a", null), seen)
    }
}
