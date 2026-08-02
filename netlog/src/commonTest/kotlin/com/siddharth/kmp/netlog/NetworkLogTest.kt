package com.siddharth.kmp.netlog

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkLogTest {
    private fun entry(headers: Map<String, String> = emptyMap(), body: String? = null) =
        NetworkLogEntry(method = "POST", url = "https://api.example.com/v1/pay", requestHeaders = headers, requestBody = body)

    @Test
    fun curlRedactsCredentialsByDefault() {
        // The property that matters: toCurl exists to be pasted somewhere else, so it must not
        // carry a live bearer token with it.
        val curl = entry(mapOf("Authorization" to "Bearer sk_live_supersecret", "Accept" to "application/json")).toCurl()
        assertFalse(curl.contains("sk_live_supersecret"), "curl leaked the token: $curl")
        assertContains(curl, "Authorization: <redacted>")
        assertContains(curl, "Accept: application/json", message = "non-sensitive headers must survive")
    }

    @Test
    fun redactionIsCaseInsensitive() {
        // Header casing is guaranteed by nothing; a lowercase 'authorization' must redact too.
        listOf("authorization", "AUTHORIZATION", "AuThOrIzAtIoN").forEach { name ->
            val curl = entry(mapOf(name to "Bearer leak")).toCurl()
            assertFalse(curl.contains("leak"), "'$name' was not redacted")
        }
    }

    @Test
    fun everyDefaultRedactedHeaderIsActuallyRedacted() {
        DEFAULT_REDACTED_HEADERS.forEach { name ->
            val curl = entry(mapOf(name to "sensitive-value")).toCurl()
            assertFalse(curl.contains("sensitive-value"), "'$name' is in the default set but leaked")
        }
    }

    @Test
    fun redactionCanBeDisabledDeliberately() {
        val curl = entry(mapOf("Authorization" to "Bearer tok")).toCurl(redactHeaders = emptySet())
        assertContains(curl, "Bearer tok")
    }

    @Test
    fun rawHeadersRemainOnTheEntryForALocalDebugScreen() {
        // Redaction is an export concern, not a storage one — a local screen may legitimately show
        // the real value. Only toCurl redacts.
        val e = entry(mapOf("Authorization" to "Bearer tok"))
        assertEquals("Bearer tok", e.requestHeaders["Authorization"])
    }

    @Test
    fun curlShapeIsWellFormed() {
        val curl = entry(mapOf("Accept" to "application/json"), body = """{"amount":100}""").toCurl()
        assertTrue(curl.startsWith("curl -X POST"), curl)
        assertContains(curl, """-d '{"amount":100}'""")
        assertTrue(curl.endsWith("'https://api.example.com/v1/pay'"), curl)
    }

    @Test
    fun storeKeepsNewestFirstAndHonoursCapacity() {
        val store = NetworkLogStore(capacity = 3)
        (1..5).forEach { store.record(entry().copy(url = "u$it")) }
        val urls = store.entries.value.map { it.url }
        assertEquals(listOf("u5", "u4", "u3"), urls, "newest first, oldest dropped")
    }

    @Test
    fun clearEmptiesTheStore() {
        val store = NetworkLogStore()
        store.record(entry())
        assertEquals(1, store.entries.value.size)
        store.clear()
        assertTrue(store.entries.value.isEmpty())
    }

    @Test
    fun aCapacityOfOneKeepsOnlyTheLatest() {
        val store = NetworkLogStore(capacity = 1)
        store.record(entry().copy(url = "first"))
        store.record(entry().copy(url = "second"))
        assertEquals(listOf("second"), store.entries.value.map { it.url })
    }
}
