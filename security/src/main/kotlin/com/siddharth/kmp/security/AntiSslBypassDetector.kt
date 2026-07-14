package com.siddharth.kmp.security

import android.os.Process
import com.siddharth.kmp.common.AppLog
import java.io.File
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private const val TAG = "AntiSslBypass"

/**
 * Best-effort detector for SSL/TLS pinning-bypass attempts. This is a **complement to** — never a replacement for — real certificate
 * pinning ([PaymentCertificatePinning]). Pinning is the actual defense; this layer tries to notice
 * when that defense has been tampered with at runtime.
 *
 * VAPT interception tooling (Frida SSL-unpinning scripts, mitmproxy, objection, ssl-kill-switch)
 * typically bypasses pinning by one of:
 *  - installing an all-trusting / custom `X509TrustManager` into the default `SSLContext`,
 *  - setting `HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }`,
 *  - loading a native `libsslunpinning` / `ssl-kill-switch` / `bypass-ssl` library.
 *
 * Techniques here:
 *  - **TrustManager introspection** — enumerate the platform default trust managers and flag any
 *    whose class name is not a known-legitimate platform implementation (see
 *    [isPermissiveTrustManagerClassName]).
 *  - **HostnameVerifier introspection** — flag a non-standard default `HostnameVerifier`, especially
 *    one that verifies an obviously-invalid host.
 *  - **Native-map scan** — scan `/proc/self/maps` for known SSL-unpinning native libraries.
 *
 * ## Functional vs best-effort
 * **Entirely best-effort and defeatable.** These checks run in-process; an attacker who can install
 * a custom TrustManager can equally hook the reflection/introspection used here. Treat every hit as
 * a *heuristic warning*, and treat the absence of hits as *no evidence*, not proof of safety. The
 * guarantee comes from pinning + a correct `network_security_config.xml`, not from this class.
 * Honored by [SecurityConfig.bypassSsl] at aggregation.
 */
object AntiSslBypassDetector {
    /** Known-legitimate platform / OkHttp trust manager class-name prefixes. */
    private val LEGITIMATE_TRUST_MANAGERS =
        listOf(
            "com.android.org.conscrypt",
            "org.conscrypt",
            "sun.security.ssl",
            "javax.net.ssl", // wrappers
        )

    /** Known-legitimate platform / OkHttp hostname verifier class-name fragments. */
    private val LEGITIMATE_HOSTNAME_VERIFIERS =
        listOf(
            "com.android.okhttp.internal.tls.OkHostnameVerifier",
            "okhttp3.internal.tls.OkHostnameVerifier",
            "com.android.org.conscrypt",
            "javax.net.ssl.HttpsURLConnection",
        )

    /** Native SSL-unpinning library markers to look for in memory maps. */
    private val NATIVE_SSL_BYPASS_LIBS =
        listOf(
            "sslunpinning",
            "ssl-unpinning",
            "ssl_unpinning",
            "ssl-kill-switch",
            "sslkillswitch",
            "bypass-ssl",
            "sslbypass",
            "trustkit-bypass",
        )

    /**
     * Runs every SSL-bypass check and returns the signal strings that fired.
     * @return list of signals; empty when no bypass indicator was found.
     */
    fun detect(): List<String> {
        val signals = mutableListOf<String>()

        detectSuspiciousTrustManager()?.let { signals += it }
        detectSuspiciousHostnameVerifier()?.let { signals += it }
        detectNativeSslBypass()?.let { signals += it }

        if (signals.isNotEmpty()) {
            AppLog.w("SSL-bypass indicators: ${signals.joinToString()}", tag = TAG)
        }
        return signals
    }

    /** Convenience boolean over [detect]. */
    fun isSslBypassSuspected(): Boolean = detect().isNotEmpty()

    /**
     * Introspects the platform default [X509TrustManager]s and flags a non-legitimate class, or one
     * that accepts all issuers (empty accepted-issuers array is a classic all-trusting stub).
     */
    private fun detectSuspiciousTrustManager(): String? =
        try {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as java.security.KeyStore?)
            tmf.trustManagers
                .filterIsInstance<X509TrustManager>()
                .firstNotNullOfOrNull { tm ->
                    val name = tm.javaClass.name
                    when {
                        isPermissiveTrustManagerClassName(name) ->
                            "ssl: permissive/custom TrustManager by name ($name)"
                        !LEGITIMATE_TRUST_MANAGERS.any { name.startsWith(it) } &&
                            tm.acceptedIssuers.isEmpty() ->
                            "ssl: non-standard TrustManager with empty acceptedIssuers ($name)"
                        else -> null
                    }
                }
        } catch (e: Exception) {
            null
        }

    private fun detectSuspiciousHostnameVerifier(): String? =
        try {
            val verifier = HttpsURLConnection.getDefaultHostnameVerifier()
            val name = verifier.javaClass.name
            if (LEGITIMATE_HOSTNAME_VERIFIERS.any { name.contains(it) }) {
                null
            } else {
                // Non-standard verifier — probe whether it rubber-stamps an obviously-invalid host.
                val acceptsGarbage =
                    try {
                        verifier.verify("this-host-does-not.exist.invalid", null)
                    } catch (e: Exception) {
                        false
                    }
                if (acceptsGarbage) {
                    "ssl: default HostnameVerifier accepts an invalid host ($name)"
                } else {
                    "ssl: non-standard default HostnameVerifier ($name)"
                }
            }
        } catch (e: Exception) {
            null
        }

    private fun detectNativeSslBypass(): String? =
        try {
            val maps = File("/proc/${Process.myPid()}/maps")
            if (maps.exists() && maps.canRead()) {
                val lower = maps.readText().lowercase()
                NATIVE_SSL_BYPASS_LIBS
                    .firstOrNull { lower.contains(it) }
                    ?.let { "ssl: native SSL-bypass library in maps ($it)" }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
}

/**
 * Pure heuristic over a TrustManager (or verifier) class name: returns `true` when the name looks
 * like an all-trusting / bypass implementation (`TrustAllManager`, `NullTrustManager`, `BypassSSL`,
 * `AllowAll…`, `…trustall`, etc.). Case-insensitive. Extracted so the matching rules are
 * JVM-unit-testable without any TLS machinery.
 */
internal fun isPermissiveTrustManagerClassName(name: String): Boolean {
    val n = name.lowercase()
    return n.contains("trustall") ||
        n.contains("allowall") ||
        n.contains("bypass") ||
        n.contains("nulltrust") ||
        (n.contains("empty") && n.contains("trust")) ||
        n.contains("insecure") ||
        n.contains("acceptall")
}
