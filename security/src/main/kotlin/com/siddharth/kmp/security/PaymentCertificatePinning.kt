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

    /**
     * Builds a [CertificatePinner] pinning two placeholder keys per provider host. Returns a
     * fully-formed pinner so the pattern is wired end-to-end, but until [isPinningActive] is true
     * the pins are inert placeholders.
     */
    fun pinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        PINNED_HOSTS.forEach { host ->
            builder.add(host, PLACEHOLDER_PRIMARY, PLACEHOLDER_BACKUP)
        }
        return builder.build()
    }

    /**
     * `false` while the pins above are placeholders. Flip to a real check (or just return `true`)
     * once real SPKI hashes are in place — callers can use this to decide whether to actually attach
     * the pinner to their OkHttp client in a production build.
     */
    fun isPinningActive(): Boolean = false
}
