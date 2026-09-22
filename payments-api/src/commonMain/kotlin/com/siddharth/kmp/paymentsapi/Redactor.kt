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

    /** Characters kept in clear at each end, so a value stays recognisable without being readable. */
    private const val VISIBLE_PREFIX = 2
    private const val VISIBLE_SUFFIX = 2

    /** Below this length there is nothing left to show once both ends are kept — mask it whole. */
    private const val MIN_MASKABLE_LENGTH = VISIBLE_PREFIX + VISIBLE_SUFFIX

    /** Cap on the bullet run, so a long token does not leak its length through the mask. */
    private const val MAX_MASK_BULLETS = 8

    /** Preserve enough shape to be recognizable (first 2 / last 2 chars) without revealing the value. */
    private fun mask(value: String): String =
        when {
            value.length <= MIN_MASKABLE_LENGTH -> "••••"
            else ->
                value.take(VISIBLE_PREFIX) +
                    "•".repeat((value.length - MIN_MASKABLE_LENGTH).coerceAtMost(MAX_MASK_BULLETS)) +
                    value.takeLast(VISIBLE_SUFFIX)
        }
}
