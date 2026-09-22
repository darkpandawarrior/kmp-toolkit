package com.siddharth.kmp.security

import okhttp3.CertificatePinner

/**
 * OkHttp [CertificatePinner] configuration for the payment-provider API domains.
 *
 * ## DEMONSTRATIVE — pins are placeholders
 * Every pin below is a **placeholder** (`sha256/AAAA…=`) and MUST be replaced with the real
 * base64-encoded SHA-256 of each host's leaf and/or intermediate **SPKI** (Subject Public Key Info)
 * before this is used against production traffic. The correct way to obtain them:
 *
 * ```
 * openssl s_client -connect api.razorpay.com:443 -servername api.razorpay.com < /dev/null 2>/dev/null \
 *   | openssl x509 -pubkey -noout \
 *   | openssl pkey -pubin -outform der \
 *   | openssl dgst -sha256 -binary \
 *   | openssl enc -base64
 * ```
 *
 * Always pin **two** keys per host (current leaf/intermediate + a backup), otherwise a routine cert
 * rotation on the provider's side bricks the app until a store update ships.
 *
 * The local dev backend (`10.0.2.2`, the emulator's host loopback) is intentionally **not** pinned —
 * its self-signed dev cert rotates freely and pinning it would just get in the way. Pinning is a
 * production hardening step; here it demonstrates the pattern.
 */
object PaymentCertificatePinning {
    // Placeholder SPKI pins — DO NOT ship. Replace with real leaf + backup pins per host.
    private const val PLACEHOLDER_PRIMARY = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private const val PLACEHOLDER_BACKUP = "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="

    private val PINNED_HOSTS: List<String> =
        listOf(
            "api.razorpay.com",
            "sandbox.cashfree.com",
            "api.cashfree.com",
            "api.stripe.com",
        )

    /** Always pin two keys per host: the current leaf/intermediate plus a rotation backup. */
    private const val PINS_REQUIRED_PER_HOST = 2

    /**
     * Real base64 SHA-256 SPKI pins, keyed by host. Empty while the placeholders above stand in.
     *
     * This map is the single fact both [pinner] and [isPinningActive] read. It replaces a hardcoded
     * `fun isPinningActive() = false`, which was a value pretending to be a computation: whoever
     * eventually pasted real pins in had to remember to flip a separate boolean, and nothing would
     * have caught them forgetting. Fill this in and both functions become correct together.
     */
    private val REAL_PINS: Map<String, List<String>> = emptyMap()

    private fun pinsFor(host: String): List<String> = REAL_PINS[host] ?: listOf(PLACEHOLDER_PRIMARY, PLACEHOLDER_BACKUP)

    /**
     * Builds a [CertificatePinner] for every host in [PINNED_HOSTS]. Returns a fully-formed pinner
     * so the pattern is wired end-to-end, but until [isPinningActive] is true the pins are inert
     * placeholders.
     */
    fun pinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        PINNED_HOSTS.forEach { host ->
            builder.add(host, *pinsFor(host).toTypedArray())
        }
        return builder.build()
    }

    /**
     * True only when every pinned host has its full set of real pins. Callers use this to decide
     * whether to attach [pinner] to their OkHttp client in a production build.
     */
    fun isPinningActive(): Boolean =
        PINNED_HOSTS.isNotEmpty() &&
            PINNED_HOSTS.all { host -> REAL_PINS[host].orEmpty().size >= PINS_REQUIRED_PER_HOST }
}
