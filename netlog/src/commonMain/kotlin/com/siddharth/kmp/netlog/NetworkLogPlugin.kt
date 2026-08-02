package com.siddharth.kmp.netlog

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.bodyAsText
import io.ktor.http.content.TextContent
import io.ktor.util.AttributeKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val StartTimeKey = AttributeKey<Long>("NetworkLogStartTime")
private val RequestBodyKey = AttributeKey<String>("NetworkLogRequestBody")

/**
 * Ktor client plugin that records every exchange into [store].
 *
 * ```
 * val logs = NetworkLogStore()
 * val client = HttpClient { install(NetworkLogPlugin(logs)) }
 * ```
 *
 * Purely local and in-memory — it never makes a network call of its own.
 *
 * ### Install it on debug builds only
 *
 * This captures request and response **bodies**, which on a real app means auth payloads, personal
 * data and anything else your API carries. That is exactly what makes it useful while debugging and
 * exactly why it should not be in a release build. Gate the `install(...)` on your own debug flag;
 * this module cannot do it for you, because "debug" is a build concept and this is common code.
 *
 * ### Known limitation, not yet resolved
 *
 * [readResponseBody] defaults to true, which calls `bodyAsText()` inside `onResponse`. Depending on
 * the Ktor version and engine, reading the body in a response hook can consume the content channel
 * so the actual caller receives an empty body. Mileway has shipped this shape without visible
 * trouble, so it is at worst latent there — but "it has not bitten yet" is not a proof, and this
 * module is meant for reuse. Pass `readResponseBody = false` if a consumer sees empty response
 * bodies after installing this; everything else still records. Resolving it properly means routing
 * through Ktor's `ResponseObserver`, which is the intended upgrade path.
 */
@OptIn(ExperimentalTime::class)
public fun NetworkLogPlugin(
    store: NetworkLogStore,
    readResponseBody: Boolean = true,
): io.ktor.client.plugins.api.ClientPlugin<Unit> =
    createClientPlugin("NetworkLogPlugin") {
        onRequest { request, content ->
            request.attributes.put(StartTimeKey, Clock.System.now().toEpochMilliseconds())
            // Only TextContent exposes its payload cheaply; multipart/streaming bodies are skipped
            // rather than buffered, which would defeat the point of streaming them.
            (content as? TextContent)?.let { request.attributes.put(RequestBodyKey, it.text) }
        }
        onResponse { response ->
            val request = response.call.request
            val startedAt = request.attributes.getOrNull(StartTimeKey)
            val now = Clock.System.now().toEpochMilliseconds()
            store.record(
                NetworkLogEntry(
                    method = request.method.value,
                    url = request.url.toString(),
                    requestHeaders = request.headers.entries().associate { it.key to it.value.joinToString(",") },
                    requestBody = request.attributes.getOrNull(RequestBodyKey),
                    status = response.status.value,
                    responseBody = if (readResponseBody) runCatching { response.bodyAsText() }.getOrNull() else null,
                    durationMs = startedAt?.let { now - it },
                    timestamp = now,
                ),
            )
        }
    }
