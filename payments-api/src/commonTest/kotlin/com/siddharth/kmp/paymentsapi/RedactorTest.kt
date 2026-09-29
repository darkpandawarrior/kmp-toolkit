package com.siddharth.kmp.paymentsapi

import kotlin.test.Test
import kotlin.test.assertEquals

class RedactorTest {
    @Test
    fun `every sensitive marker masks the value and preserves the key`() {
        val markers =
            listOf(
                "secret",
                "signature",
                "sign",
                "key",
                "token",
                "password",
                "pwd",
                "cvv",
                "cvc",
                "card",
                "pan",
                "otp",
                "auth",
                "vpa",
                "email",
                "phone",
                "contact",
            )

        for (marker in markers) {
            val result = Redactor.redact("request", mapOf(marker to "abcdefgh"))

            assertEquals(listOf(marker to "ab••••gh"), result.entries, marker)
        }
    }

    @Test
    fun `non sensitive fields pass through verbatim`() {
        val raw = linkedMapOf("status" to "  Approved\n", "amount" to "001.00", "note" to "secret token", "empty" to "")

        val result = Redactor.redact("response", raw)

        assertEquals(raw.toList(), result.entries)
    }

    @Test
    fun `null values are absent from entries for sensitive and ordinary keys`() {
        val raw = linkedMapOf("token" to null, "status" to "ok", "note" to null)

        val result = Redactor.redact("response", raw)

        assertEquals(listOf("status" to "ok"), result.entries)
        assertEquals(emptyList(), Redactor.redact("response", mapOf("password" to null)).entries)
    }

    @Test
    fun `values of zero through four characters are masked completely`() {
        for (value in listOf("", "a", "ab", "abc", "abcd")) {
            val result = Redactor.redact("request", mapOf("password" to value))

            assertEquals(listOf("password" to "••••"), result.entries, "length ${value.length}")
        }
    }

    @Test
    fun `longer values keep two characters at each end and mask the middle`() {
        for ((value, expected) in listOf("abcde" to "ab•de", "abcdefgh" to "ab••••gh")) {
            val result = Redactor.redact("request", mapOf("token" to value))

            assertEquals(listOf("token" to expected), result.entries, value)
        }
    }

    @Test
    fun `mask bullet run is capped at eight`() {
        for (value in listOf("ab12345678yz", "ab123456789yz", "abcdefghijklmnopqrstuvwxyz012345yz")) {
            val result = Redactor.redact("request", mapOf("token" to value))

            assertEquals(listOf("token" to "ab••••••••yz"), result.entries, value)
        }
    }

    @Test
    fun `sensitive matching is case insensitive and substring based`() {
        for (key in listOf("CardNumber", "authToken", "prefix_ToKeN_suffix", "monkey", "company")) {
            val result = Redactor.redact("request", mapOf(key to "abcdefgh"))

            assertEquals(listOf(key to "ab••••gh"), result.entries, key)
        }
    }

    @Test
    fun `label and input iteration order are preserved`() {
        val raw = linkedMapOf("z_status" to "ok", "token" to "abcdefgh", "missing" to null, "a_amount" to "100")

        val result = Redactor.redact(" Provider response ", raw)

        assertEquals(" Provider response ", result.label)
        assertEquals(listOf("z_status" to "ok", "token" to "ab••••gh", "a_amount" to "100"), result.entries)
    }
}
