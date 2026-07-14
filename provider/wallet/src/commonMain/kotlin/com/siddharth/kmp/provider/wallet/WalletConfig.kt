package com.siddharth.kmp.provider.wallet

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus

/**
 * Archetype-E (internal rail): a backend double-entry ledger, no external SDK at all. Unlike every
 * other provider archetype, there is no sandbox to be "ready" for — the ledger IS the whole
 * implementation, so this is always [GatewayStatus.SANDBOX_READY].
 */
data class WalletConfig(
    val gatewayId: GatewayId,
    val displayName: String,
    /** The ledger account id this wallet debits/credits from — one per demo user. */
    val walletAccountId: String,
    val region: String = "Global",
    val docsPath: String,
    val blurb: String,
    // Honest capabilities: no 3DS, no webhook — it's a synchronous internal balance movement.
    val capabilities: Set<Capability> = setOf(Capability.ONE_TIME_PAYMENT, Capability.WALLET, Capability.REFUND),
    val status: GatewayStatus = GatewayStatus.SANDBOX_READY,
)
