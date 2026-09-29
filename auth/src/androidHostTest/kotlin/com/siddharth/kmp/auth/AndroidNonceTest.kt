package com.siddharth.kmp.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity check against [IosNonce]'s `require(bytes > 0)` guard: `newRawNonce(0)` must not silently
 * hand back an empty (worthless) nonce, and `newRawNonce(-1)` must fail with a clear contract
 * violation rather than a raw `NegativeArraySizeException` from `ByteArray(bytes)`.
 */
class AndroidNonceTest {
    @Test
    fun `zero bytes throws IllegalArgumentException`() {
        val exception = assertThrows(IllegalArgumentException::class.java) { newRawNonce(0) }
        assertTrue(exception.message.orEmpty().contains("nonce length must be positive"))
    }

    @Test
    fun `negative bytes throws IllegalArgumentException, not NegativeArraySizeException`() {
        val exception = assertThrows(IllegalArgumentException::class.java) { newRawNonce(-1) }
        assertTrue(exception.message.orEmpty().contains("nonce length must be positive"))
    }

    @Test
    fun `default byte count still returns a 64-character lowercase-hex nonce`() {
        val nonce = newRawNonce(32)
        assertEquals(64, nonce.length)
        assertTrue(nonce.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
