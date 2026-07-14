package com.siddharth.kmp.provider.xendit

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus

/**
 * Xendit e-wallet (DANA/OVO/LinkAja) — genuinely archetype-D, not archetype-C: confirmation happens
 * on the payer's e-wallet app outside this one, so there is no synchronous checkout result to render
 * in a WebView. Same PENDING/AWAITING_WEBHOOK shape as [com.siddharth.kmp.provider.mobilemoney].
 */
data class XenditConfig(
    val gatewayId: GatewayId = GatewayId("xendit"),
    val displayName: String = "Xendit",
    val region: String = "Indonesia",
    val docsPath: String = "docs/providers/xendit.md",
    val blurb: String = "DANA/OVO/LinkAja e-wallets; confirmation in the payer's wallet app, no in-app SDK.",
    val capabilities: Set<Capability> = setOf(Capability.ONE_TIME_PAYMENT, Capability.WALLET),
    val status: GatewayStatus = GatewayStatus.MOCK_MODE,
)
