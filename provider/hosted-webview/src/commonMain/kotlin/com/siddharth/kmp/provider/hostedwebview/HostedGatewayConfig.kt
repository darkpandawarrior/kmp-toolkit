package com.siddharth.kmp.provider.hostedwebview

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus

/**
 * One hosted-checkout gateway's shape. `provider:hosted-webview` is a single archetype-C module that
 * takes N of these instead of a module per gateway (dozens of hosted gateways would otherwise blow up
 * the build graph).
 */
data class HostedGatewayConfig(
    val gatewayId: GatewayId,
    val displayName: String,
    val region: String,
    val docsPath: String,
    val blurb: String,
    val capabilities: Set<Capability> = setOf(Capability.ONE_TIME_PAYMENT, Capability.CARDS),
    val status: GatewayStatus = GatewayStatus.MOCK_MODE,
    /** Builds the hosted checkout URL from the backend's provider params (`checkout_url`, etc.). */
    val buildCheckoutUrl: (Map<String, String>) -> String,
    /** Return-URL interception: maps a loaded URL to a terminal outcome, or null if not yet terminal. */
    val matchReturn: (String) -> HostedReturnOutcome?,
)

/** The terminal outcome detected from a return-URL redirect inside the hosted checkout WebView. */
sealed interface HostedReturnOutcome {
    data class Success(
        val paymentId: String?,
    ) : HostedReturnOutcome

    data class Failure(
        val reason: String?,
    ) : HostedReturnOutcome

    data object Cancelled : HostedReturnOutcome
}

/**
 * Common return-URL shapes seen across hosted gateways: a success/failure path or query marker,
 * optionally carrying a payment id. Most configs can be expressed with this alone; gateways with a
 * stranger return contract supply a custom [matchReturn].
 */
object ReturnUrlMatchers {
    fun byMarker(
        successMarker: String,
        failureMarker: String,
        paymentIdParam: String = "payment_id",
        reasonParam: String = "reason",
    ): (String) -> HostedReturnOutcome? =
        { url ->
            when {
                url.contains(successMarker) -> HostedReturnOutcome.Success(queryParam(url, paymentIdParam))
                url.contains(failureMarker) -> HostedReturnOutcome.Failure(queryParam(url, reasonParam))
                else -> null
            }
        }

    private fun queryParam(
        url: String,
        name: String,
    ): String? {
        val marker = "$name="
        val start = url.indexOf(marker).takeIf { it != -1 }?.plus(marker.length) ?: return null
        val end = url.indexOf('&', start).let { if (it == -1) url.length else it }
        return url.substring(start, end).ifBlank { null }
    }
}
