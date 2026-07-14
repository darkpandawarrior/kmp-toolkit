package com.siddharth.kmp.provider.mobilemoney

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus

/**
 * One archetype-D (async mobile-money) gateway. Unlike archetype C (hosted-webview), there is no
 * synchronous UI at all — the user confirms on their phone (SMS/USSD/STK push) outside the app, so
 * `MobileMoneyGateway.pay()` never launches anything; it just returns `Pending` and lets the
 * orchestrator's existing poll-with-backoff (already built for UPI intent's `SUBMITTED` limbo) find
 * out what happened.
 */
data class MobileMoneyConfig(
    val gatewayId: GatewayId,
    val displayName: String,
    val region: String,
    val docsPath: String,
    val blurb: String,
    val capabilities: Set<Capability> = setOf(Capability.ONE_TIME_PAYMENT, Capability.WALLET),
    val status: GatewayStatus = GatewayStatus.MOCK_MODE,
)
