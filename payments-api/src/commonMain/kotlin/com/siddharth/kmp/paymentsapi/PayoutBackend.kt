package com.siddharth.kmp.paymentsapi

/**
 * The app's view of the server's Transfers/payout rail (roadmap #4) — mirrors [PaymentBackend]'s
 * shape: [initiate] does the server round-trip, [status] polls the server-authoritative state a mock
 * settlement webhook updates. Implemented in `core:network` against `core:protocol` DTOs.
 *
 * There is no `pay`/SDK-launch step here — a real payout rail moves money to a recipient without any
 * client UI, and it's KYC-gated (see [GatewayStatus]'s own doc comment), so this app models only the
 * honest initiate → PENDING → settled lifecycle, never a fake instant success.
 */
interface PayoutBackend {
    /**
     * `POST /payouts`. [idempotencyKey] must be stable across retries of the SAME logical payout
     * attempt, same contract as [PaymentBackend.createOrder]'s.
     */
    suspend fun initiate(
        gatewayId: GatewayId,
        recipientRef: String,
        amount: Money,
        idempotencyKey: String,
    ): PayoutSnapshot

    /** `GET /payouts/{id}` — poll the server-authoritative state. */
    suspend fun status(payoutId: String): PayoutSnapshot
}

/** Payout lifecycle — the domain mirror of `PayoutStatusDto`. */
enum class PayoutStatus {
    PENDING,
    SETTLED,
    FAILED,
    ;

    val isTerminal: Boolean
        get() = this == SETTLED || this == FAILED
}

data class PayoutSnapshot(
    val payoutId: String,
    val gatewayId: GatewayId,
    val recipientRef: String,
    val amount: Money,
    val status: PayoutStatus,
)
