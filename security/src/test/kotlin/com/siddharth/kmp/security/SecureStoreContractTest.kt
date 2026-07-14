package com.siddharth.kmp.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the [SecureStore] contract against [FakeSecureStore]. The Keystore-backed
 * implementation needs a real device/TEE, so the contract is verified here on the fake and only
 * smoke-tested on-device.
 */
class SecureStoreContractTest {
    private lateinit var store: SecureStore

    @Before
    fun setUp() {
        store = FakeSecureStore()
    }

    @Test
    fun `put then get returns the stored value`() {
        store.putString("token", "tok_abc123")
        assertEquals("tok_abc123", store.getString("token"))
    }

    @Test
    fun `get on a missing key returns null`() {
        assertNull(store.getString("absent"))
    }

    @Test
    fun `put overwrites an existing value`() {
        store.putString("token", "old")
        store.putString("token", "new")
        assertEquals("new", store.getString("token"))
    }

    @Test
    fun `contains reflects presence and absence`() {
        assertFalse(store.contains("token"))
        store.putString("token", "v")
        assertTrue(store.contains("token"))
    }

    @Test
    fun `remove deletes a single key`() {
        store.putString("a", "1")
        store.putString("b", "2")
        store.remove("a")
        assertFalse(store.contains("a"))
        assertTrue(store.contains("b"))
        assertEquals("2", store.getString("b"))
    }

    @Test
    fun `clear removes everything`() {
        store.putString("a", "1")
        store.putString("b", "2")
        store.clear()
        assertFalse(store.contains("a"))
        assertFalse(store.contains("b"))
        assertNull(store.getString("a"))
    }

    @Test
    fun `fake can be seeded with initial values`() {
        val seeded = FakeSecureStore(mapOf("k" to "v"))
        assertEquals("v", seeded.getString("k"))
    }
}
