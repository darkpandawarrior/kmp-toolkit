package com.siddharth.kmp.paymentsapi

/**
 * A key/value snapshot of an SDK request or response that has already passed the redaction
 * allowlist — safe to render in the Lab timeline and to log. Constructing one asserts "these fields
 * are non-secret". Secrets and PII never reach this type; the redactor drops them upstream.
 */
data class RedactedPayload(
    val label: String,
    val entries: List<Pair<String, String>>,
) {
    companion object {
        val EMPTY = RedactedPayload(label = "", entries = emptyList())

        fun of(
            label: String,
            vararg entries: Pair<String, String>,
        ): RedactedPayload = RedactedPayload(label, entries.toList())
    }
}
