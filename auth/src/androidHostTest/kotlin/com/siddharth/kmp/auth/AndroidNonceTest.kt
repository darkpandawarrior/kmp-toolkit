package com.siddharth.kmp.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidNonceTest {
    @Test
    fun zeroLengthIsRejected() {
        val error = assertFailsWith<IllegalArgumentException> { newRawNonce(0) }
        assertTrue(error.message.orEmpty().contains("nonce length must be positive"))
    }

    @Test
    fun negativeLengthIsRejected() {
        val error = assertFailsWith<IllegalArgumentException> { newRawNonce(-1) }
        assertTrue(error.message.orEmpty().contains("nonce length must be positive"))
    }

    @Test
    fun thirtyTwoBytesProduceLowercaseHex() {
        val nonce = newRawNonce(32)
        assertEquals(64, nonce.length)
        assertTrue(nonce.matches(Regex("[0-9a-f]{64}")))
    }
}
