package com.siddharth.kmp.paymentsapi

import kotlinx.coroutines.flow.Flow

/**
 * The process-death insurance policy. A pending row is written here *before* the SDK launches, so if
 * the app is killed mid-payment (OEM battery kill, user swipe, low memory during the bank's 3DS
 * WebView) the orchestrator can, on next cold start, find unresolved payments and reconcile them
 * against the server. Implemented by `core:data` (Room).
 */
interface PendingPaymentJournal {
    /** Record an in-flight payment before launching the gateway. */
    suspend fun record(entry: PendingPayment)

    /** Mark a payment resolved with its server-authoritative terminal status. */
    suspend fun markResolved(
        orderId: String,
        status: PaymentStatus,
        paymentId: String?,
    )

    /** Payments written but not yet resolved — the recovery work list on cold start. */
    suspend fun unresolved(): List<PendingPayment>

    /** Full history stream for the History feature. */
    fun observeAll(): Flow<List<PendingPayment>>
}

/** A durable record of one payment attempt. */
data class PendingPayment(
    val orderId: String,
    val catalogItemId: String,
    val gatewayId: GatewayId,
    val amount: Money,
    val createdAtEpochMs: Long,
    val status: PaymentStatus,
    val paymentId: String? = null,
)
