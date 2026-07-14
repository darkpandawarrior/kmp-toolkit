package com.siddharth.kmp.paymentsapi

import com.siddharth.kmp.common.UiText

/**
 * The terminal (or near-terminal) outcome of a payment attempt, as reported by the client SDK.
 *
 * Critical invariant this app teaches: a [Success] here is a *hint*, not proof. Only the backend —
 * after signature verification and webhook reconciliation — decides the true state. The orchestrator
 * always confirms server-side before trusting a client [Success].
 */
sealed interface PaymentResult {
    val raw: RedactedPayload

    data class Success(
        val paymentId: String,
        /**
         * Fields the server needs to verify this payment (signature, order id, gateway refs).
         * NOT for display — the orchestrator forwards these to [PaymentBackend.verify] only. The
         * human-readable, secret-safe view is [raw].
         */
        val verification: Map<String, String>,
        override val raw: RedactedPayload,
    ) : PaymentResult

    data class Failure(
        val code: FailureCode,
        val message: UiText,
        override val raw: RedactedPayload,
    ) : PaymentResult

    /** Not yet terminal — e.g. UPI `SUBMITTED`. Requires polling the backend to resolve. */
    data class Pending(
        val reason: PendingReason,
        val verification: Map<String, String> = emptyMap(),
        override val raw: RedactedPayload = RedactedPayload.EMPTY,
    ) : PaymentResult

    data class Cancelled(
        override val raw: RedactedPayload = RedactedPayload.EMPTY,
    ) : PaymentResult
}

/** Normalized failure taxonomy — every provider maps its own SDK error zoo into these. */
enum class FailureCode {
    USER_CANCELLED,
    NETWORK_ERROR,
    GATEWAY_DECLINED,
    VERIFICATION_FAILED,
    CONFIG_MISSING,
    SDK_ERROR,
}

/** Why a payment is not yet terminal. */
enum class PendingReason {
    UPI_SUBMITTED,
    AWAITING_WEBHOOK,
    UNDER_REVIEW,
}
