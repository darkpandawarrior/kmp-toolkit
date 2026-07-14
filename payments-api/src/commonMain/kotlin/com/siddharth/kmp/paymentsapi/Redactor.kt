package com.siddharth.kmp.paymentsapi

/**
 * Turns an arbitrary provider request/response map into a [RedactedPayload] safe to render and log.
 *
 * Redaction is deny-by-pattern: any key that looks like a secret or PII is masked to its shape
 * (never dropped silently — the Lab still shows the field existed, which is itself educational). A
 * client that renders raw gateway payloads is a leak waiting to happen; this is the single choke
 * point that prevents it.
 */
object Redactor {
    private val sensitiveMarkers =
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

    fun redact(
        label: String,
        raw: Map<String, String?>,
    ): RedactedPayload {
        val entries =
            raw.entries
                .filter { it.value != null }
                .map { (k, v) -> k to maskIfSensitive(k, v!!) }
        return RedactedPayload(label, entries)
    }

    private fun maskIfSensitive(
        key: String,
        value: String,
    ): String {
        val lower = key.lowercase()
        val sensitive = sensitiveMarkers.any { lower.contains(it) }
        return if (sensitive) mask(value) else value
    }

    /** Preserve enough shape to be recognizable (first 2 / last 2 chars) without revealing the value. */
    private fun mask(value: String): String =
        when {
            value.length <= 4 -> "••••"
            else -> value.take(2) + "•".repeat((value.length - 4).coerceAtMost(8)) + value.takeLast(2)
        }
}
