package com.siddharth.kmp.provider.mpesa

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus

/**
 * M-Pesa Daraja (STK push) — its own module (rather than folding into the generic
 * [com.siddharth.kmp.provider.mobilemoney] fan-out) so it gets its own mock webhook settle route
 * (`POST /mock/mpesa/{orderId}/settle`) instead of the shared `/mock/momo/{provider}` delayed-flip.
 * Same PENDING/AWAITING_WEBHOOK result — only the settle route ownership changes.
 */
data class MpesaConfig(
    val gatewayId: GatewayId = GatewayId("mpesa"),
    val displayName: String = "M-Pesa",
    val region: String = "Kenya/Tanzania",
    val docsPath: String = "docs/providers/mpesa.md",
    val blurb: String = "Daraja STK push — confirmation happens on the payer's phone, no in-app SDK/UI.",
    val capabilities: Set<Capability> = setOf(Capability.ONE_TIME_PAYMENT, Capability.WALLET),
    val status: GatewayStatus = GatewayStatus.MOCK_MODE,
)
