package com.siddharth.kmp.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
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

/** The platform HTTP engine: OkHttp (android), Darwin (ios), CIO (jvm/desktop). */
internal expect fun httpClientEngine(): HttpClientEngine

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
 */
fun createHttpClient(
    engine: HttpClientEngine = httpClientEngine(),
    onUnauthorized: suspend () -> Unit = {},
): HttpClient =
    HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) { json(networkJson) }
        install(Logging) { level = LogLevel.INFO }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, request ->
                val status = (cause as? ResponseException)?.response?.status
                if (status == HttpStatusCode.Unauthorized && !request.url.encodedPath.contains("/auth/")) {
                    onUnauthorized()
                }
                // Re-throw is implicit: this handler only observes; expectSuccess still surfaces the
                // exception to the caller so the outbox/repository logic classifies it as usual.
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            // requestTimeoutMillis bounds the whole call, including streaming the response body — a
            // 30s cap would kill the long-lived /api/scan stream. streamScan() overrides this to
            // INFINITE per-request for a long-lived streaming call; every other call keeps the 30s default.
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnExceptionIf(maxRetries = 3) { _, cause -> cause !is kotlinx.coroutines.CancellationException }
            exponentialDelay()
        }
    }
