package com.siddharth.kmp.paymentsapi

import kotlinx.serialization.Serializable

// ── Wallet ledger wire format (provider:wallet, the internal-rail archetype) ───────────────────
// Lives next to WalletLedgerPort: both the port contract and its HTTP wire shape are payment-domain
// concerns shared by provider:wallet's client-side gateway and any server implementing the same
// `/wallet/{accountId}/...` routes (e.g. a consumer's own mock backend).

/** `GET /wallet/{accountId}/balance` response. */
@Serializable
data class WalletBalanceResponse(
    val accountId: String,
    val balanceMinor: Long,
)

/** `POST /wallet/{accountId}/debit` request — the ledger "pay" movement, carries an idempotency key. */
@Serializable
data class WalletDebitRequest(
    val idempotencyKey: String,
    val amountMinor: Long,
)

/** `POST /wallet/{accountId}/refund` request — the ledger "refund" movement (a credit back). */
@Serializable
data class WalletRefundRequest(
    val idempotencyKey: String,
    val amountMinor: Long,
)

@Serializable
data class WalletTransactionResponse(
    val txnId: String,
    val accountId: String,
    val balanceMinor: Long,
)
