package com.siddharth.kmp.paymentsapi

/**
 * Slim port onto the wallet's double-entry ledger, used ONLY by the orchestrator (see
 * `PaymentOrchestrator.paySplit`) for the split-payment wallet leg's compensating credit.
 * Deliberately separate from [PaymentGateway] — refund/compensation is an orchestration concern
 * (per the split-payment design), not part of the gateway contract every provider implements.
 *
 * Implemented in `provider:wallet` against the same backend ledger [WalletGateway] debits via HTTP.
 */
interface WalletLedgerPort {
    /** Idempotently debit [walletAccountId] by [amountMinor]. Throws if the balance is insufficient. */
    suspend fun debit(
        walletAccountId: String,
        idempotencyKey: String,
        amountMinor: Long,
    ): String

    /** Idempotently credit [walletAccountId] back by [amountMinor] — the split-payment compensation. */
    suspend fun refund(
        walletAccountId: String,
        idempotencyKey: String,
        amountMinor: Long,
    ): String
}

/** Thrown by [WalletLedgerPort.debit] when the wallet balance can't cover the requested amount. */
class InsufficientWalletBalanceException(
    walletAccountId: String,
) : Exception("Wallet account $walletAccountId has insufficient balance for this debit")
