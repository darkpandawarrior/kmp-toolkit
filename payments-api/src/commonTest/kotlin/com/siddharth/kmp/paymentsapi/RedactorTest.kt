package com.siddharth.kmp.paymentsapi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RedactorTest {
    @Test
    fun `sensitive key masks the value not the key`() {
        val result = Redactor.redact("gw", mapOf("cardNumber" to "4111111111111111"))

        val (key, value) = result.entries.single()
        assertEquals("cardNumber", key)
        assertFalse(value.contains("4111111111111111"))
    }

    @Test
    fun `non sensitive key passes value through unmasked`() {
        val result = Redactor.redact("gw", mapOf("orderId" to "order_42"))

        assertEquals("order_42", result.entries.toMap()["orderId"])
    }

    @Test
    fun `null value is dropped entirely`() {
        val result = Redactor.redact("gw", mapOf("token" to null, "orderId" to "order_1"))

        assertEquals(listOf("orderId"), result.entries.map { it.first })
    }

    @Test
    fun `short value below the visible window masks to a fixed bullet block`() {
        val result = Redactor.redact("gw", mapOf("pwd" to "abcd"))

        assertEquals("••••", result.entries.toMap()["pwd"])
    }

    @Test
    fun `long value keeps visible ends and caps the bullet run`() {
        val value = "a".repeat(15) + "bc" + "d".repeat(13)
        val result = Redactor.redact("gw", mapOf("authToken" to value))

        assertEquals("aa" + "•".repeat(8) + "dd", result.entries.toMap()["authToken"])
    }

    @Test
    fun `value just above the visible window masks the full remaining middle`() {
        val result = Redactor.redact("gw", mapOf("secret" to "abcdefg"))

        // length 7: middle = 7 - 4 = 3, below the 8-char cap
        assertEquals("ab" + "•".repeat(3) + "fg", result.entries.toMap()["secret"])
    }

    @Test
    fun `matching is case insensitive and substring based`() {
        val result = Redactor.redact("gw", mapOf("CardNumber" to "abcdefgh", "authToken" to "abcdefgh"))

        assertFalse(result.entries.toMap().getValue("CardNumber").contains("abcdefgh"))
        assertFalse(result.entries.toMap().getValue("authToken").contains("abcdefgh"))
    }

    @Test
    fun `redact preserves label and input entry order`() {
        val result =
            Redactor.redact(
                "checkout",
                linkedMapOf(
                    "orderId" to "order_1",
                    "email" to "a@b.com",
                    "status" to "ok",
                ),
            )

        assertEquals("checkout", result.label)
        assertEquals(listOf("orderId", "email", "status"), result.entries.map { it.first })
    }
}
