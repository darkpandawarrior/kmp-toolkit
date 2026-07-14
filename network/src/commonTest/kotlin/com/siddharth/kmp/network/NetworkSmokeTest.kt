package com.siddharth.kmp.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkSmokeTest {
    @Test
    fun networkJson_ignoresUnknownKeys() {
        // Forward-compat with a newer server: unknown fields must not blow up decoding.
        val obj = networkJson.decodeFromString<JsonObject>("""{"known":1,"unknown":"x"}""")
        assertTrue(obj.containsKey("known"))
    }

    @Test
    fun alwaysOnlineChecker_isOnline() {
        assertTrue(AlwaysOnlineConnectivityChecker.isOnline())
    }

    @Test
    fun createHttpClient_negotiatesJson() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client: HttpClient = createHttpClient(engine = engine)
        assertEquals("""{"ok":true}""", client.get("https://example.test/ping").bodyAsText())
    }
}
