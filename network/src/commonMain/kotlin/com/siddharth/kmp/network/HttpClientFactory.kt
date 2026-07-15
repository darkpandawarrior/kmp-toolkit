package com.siddharth.kmp.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Resolves the server base URL at call time (backed by DataStore in core:data; defaults to localhost). */
fun interface BaseUrlProvider {
    suspend fun baseUrl(): String
}

/** Optional bearer token for authenticated deployments — returns null when the server is open. */
fun interface TokenProvider {
    suspend fun token(): String?
}

/**
 * Invoked when any authenticated call comes back 401 (token expired/revoked). The core:data
 * SessionManager binds this to clear the stored token and flip the shell into re-login. Kept as a
 * seam (like [TokenProvider]) so core:network needn't depend on core:data. Default = no-op.
 *
 * Cycle note: the client resolves this lazily (only when a 401 actually fires), so the DI graph
 * client → handler → AuthApi → client has no construction-time edge back to the client.
 */
fun interface UnauthorizedHandler {
    suspend fun onUnauthorized()
}

/**
 * The platform HTTP engine: OkHttp (android), Darwin (ios), CIO (jvm/desktop), Js (wasmJs).
 * Public so a consumer can build a bare [HttpClient] with its own config (e.g. a WebSocket client)
 * instead of going through [createHttpClient].
 */
expect fun httpClientEngine(): HttpClientEngine

/**
 * Lenient JSON matching the server: unknown keys ignored (forward-compat with a newer server),
 * defaults encoded so partial DTOs round-trip.
 */
val networkJson: Json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

/**
 * The shared client: content negotiation, INFO logging, a bounded exponential-backoff retry on
 * transient (5xx / IO) failures, and a request timeout. Base URL and auth are applied per-call by
 * the consumer's typed API layer (they change at runtime, so they can't live in a static defaultRequest).
 *
 * [onUnauthorized] fires once per 401 response on a NON-auth route — the auth endpoints under
 * `/api/auth/` are skipped so a bad-credentials login 401 doesn't trigger a token clear /
 * re-login loop. Wired lazily by networkModule to the bound [UnauthorizedHandler].
 *
 * [logger] routes Ktor's request/response log lines — defaults to [Logger.DEFAULT] (platform
 * println/Logcat), same as before this parameter existed. Pass a consumer's own facade (e.g. an
 * AppLog-backed `Logger`) to fold HTTP logging into the app's existing log pipeline instead.
 *
 * [expectSuccess]/[retry]/[requestTimeoutMillis] default to the original hardcoded behavior
 * (throw-on-non-2xx, bounded retry, 30s timeout) so existing callers are unaffected. A long-lived
 * WebSocket or manual-status-handling client (Kursi's RoomApi) should pass
 * `expectSuccess = false, retry = false, requestTimeoutMillis = null` — expectSuccess=true would
 * throw on every non-2xx instead of letting the caller inspect the status, retry doesn't make sense
 * for a socket upgrade, and a 30s requestTimeoutMillis would kill a long-lived connection.
 */
fun createHttpClient(
    engine: HttpClientEngine = httpClientEngine(),
    onUnauthorized: suspend () -> Unit = {},
    logger: Logger = Logger.DEFAULT,
    expectSuccess: Boolean = true,
    retry: Boolean = true,
    requestTimeoutMillis: Long? = 30_000,
): HttpClient =
    HttpClient(engine) {
        this.expectSuccess = expectSuccess
        install(ContentNegotiation) { json(networkJson) }
        install(Logging) {
            this.logger = logger
            level = LogLevel.INFO
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, request ->
                val status = (cause as? ResponseException)?.response?.status
                if (status == HttpStatusCode.Unauthorized && !request.url.encodedPath.contains("/auth/")) {
                    onUnauthorized()
                }
                // Re-throw is implicit: this handler only observes; expectSuccess still surfaces the
                // exception to the caller so the outbox/repository logic classifies it as usual.
                // (No-op when expectSuccess=false — Ktor never raises ResponseException for a
                // non-2xx in that mode, so 401 handling becomes the caller's job, by design.)
            }
        }
        install(HttpTimeout) {
            this.requestTimeoutMillis = requestTimeoutMillis
            connectTimeoutMillis = 15_000
            // requestTimeoutMillis bounds the whole call, including streaming the response body — a
            // 30s cap would kill the long-lived /api/scan stream. streamScan() overrides this to
            // INFINITE per-request for a long-lived streaming call; every other call keeps the 30s
            // default unless the caller passes requestTimeoutMillis = null up front (e.g. a socket).
        }
        if (retry) {
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                retryOnExceptionIf(maxRetries = 3) { _, cause -> cause !is kotlinx.coroutines.CancellationException }
                exponentialDelay()
            }
        }
    }
