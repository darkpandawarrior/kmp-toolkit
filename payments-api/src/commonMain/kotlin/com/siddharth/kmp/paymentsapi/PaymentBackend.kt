package com.siddharth.kmp.paymentsapi

/**
 * The app's view of the server. Implemented in `core:network` (Ktor) against the `core:protocol`
 * DTOs; consumed by the orchestrator in domain terms only, so the orchestrator never sees a DTO or
 * an HTTP concern. This is the seam that lets the tested core run against a fake backend.
 */
interface PaymentBackend {
    /**
     * `POST /orders` — price is resolved server-side from [catalogItemId]; the client never sets it.
     *
     * [idempotencyKey] must be stable across retries of the SAME logical order attempt (generated
     * once by the caller per order attempt) so a retried call dedups server-side instead of creating
     * a second live order.
     */
    suspend fun createOrder(
        catalogItemId: String,
        gatewayId: GatewayId,
        idempotencyKey: String,
    ): CreatedOrder

    /** `POST /payments/{id}/verify` — hand the client's proof to the server for authoritative checking. */
    suspend fun verify(request: VerificationRequest): PaymentSnapshot

    /** `GET /payments/{id}` — poll the server-authoritative state (updated by webhooks). */
    suspend fun status(orderId: String): PaymentSnapshot
}

/** Server-authoritative payment state — the domain mirror of `PaymentStatusDto`. */
enum class PaymentStatus {
    CREATED,
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED,
    REFUNDED,
    ;

    val isTerminal: Boolean
        get() = this == SUCCESS || this == FAILED || this == CANCELLED || this == REFUNDED
}

/** Result of order creation: the order plus the provider-specific session material for the SDK. */
data class CreatedOrder(
    val order: OrderRef,
    val gatewayId: GatewayId,
    val providerParams: Map<String, String>,
)

data class VerificationRequest(
    val gatewayId: GatewayId,
    val orderId: String,
    val paymentId: String? = null,
    val signature: String? = null,
    val extra: Map<String, String> = emptyMap(),
)

/** A point-in-time snapshot of server-side payment state. */
data class PaymentSnapshot(
    val orderId: String,
    val paymentId: String?,
    val status: PaymentStatus,
    val providerRef: String? = null,
)
