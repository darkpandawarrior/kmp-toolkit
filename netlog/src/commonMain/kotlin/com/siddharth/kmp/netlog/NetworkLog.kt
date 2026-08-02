package com.siddharth.kmp.netlog

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One recorded HTTP exchange. Produced by `NetworkLogPlugin`, rendered by whatever debug UI you build. */
data class NetworkLogEntry(
    val method: String,
    val url: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val status: Int? = null,
    val responseBody: String? = null,
    val durationMs: Long? = null,
    val timestamp: Long = 0L,
)

/**
 * Header names redacted by [toCurl]. Matched case-insensitively, since header casing is not
 * guaranteed by anything.
 *
 * `Authorization` is the one that matters: the entire purpose of [toCurl] is to produce something you
 * paste somewhere else — a terminal, a ticket, a chat thread — and an unredacted bearer token pasted
 * into any of those is a live credential leak. A debug affordance that quietly exfiltrates
 * credentials is worse than no debug affordance.
 */
public val DEFAULT_REDACTED_HEADERS: Set<String> =
    setOf("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "api-key")

/**
 * Renders this entry as a copy-pasteable `curl` command for local replay.
 *
 * Sensitive headers are replaced with `<redacted>` by default — see [DEFAULT_REDACTED_HEADERS]. Pass
 * an empty set to disable that, but understand what you are pasting: the raw values are still on
 * [NetworkLogEntry.requestHeaders], so a local debug screen can show them without this function
 * being the thing that leaks them.
 *
 * ponytail: no shell-escaping of embedded quotes. A body containing `'` produces a command you have
 * to fix by hand. Proper escaping is a real parser and this is a debug convenience — the ceiling is
 * deliberate, not overlooked.
 */
public fun NetworkLogEntry.toCurl(
    redactHeaders: Set<String> = DEFAULT_REDACTED_HEADERS,
): String {
    val redact = redactHeaders.map { it.lowercase() }.toSet()
    return buildString {
        append("curl -X ").append(method)
        requestHeaders.forEach { (name, value) ->
            val shown = if (name.lowercase() in redact) "<redacted>" else value
            append(" -H '").append(name).append(": ").append(shown).append("'")
        }
        requestBody?.let { append(" -d '").append(it).append("'") }
        append(" '").append(url).append("'")
    }
}

/**
 * In-memory ring buffer of the most recent [capacity] exchanges, newest first.
 *
 * Deliberately not persisted: log history that survives process death is a file full of request and
 * response bodies sitting on disk, which is a liability rather than a feature. It is also why this
 * needs no database and works identically on every target.
 *
 * ponytail: `record` rebuilds the list each call. At the default 200 entries that is nothing; if a
 * consumer ever logs at a rate where it matters, an ArrayDeque behind the StateFlow is the upgrade.
 */
public class NetworkLogStore(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val _entries = MutableStateFlow<List<NetworkLogEntry>>(emptyList())
    public val entries: StateFlow<List<NetworkLogEntry>> = _entries.asStateFlow()

    public fun record(entry: NetworkLogEntry) {
        _entries.update { (listOf(entry) + it).take(capacity) }
    }

    public fun clear() {
        _entries.value = emptyList()
    }

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 200
    }
}
