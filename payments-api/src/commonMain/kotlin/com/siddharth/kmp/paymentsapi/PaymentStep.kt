package com.siddharth.kmp.paymentsapi

import com.siddharth.kmp.common.UiText

/**
 * One observable step in a payment's lifecycle, emitted by the orchestrator as a stream. The Lab
 * screen renders these as a live timeline (order created → launching → client result → verifying →
 * terminal), each carrying its [RedactedPayload] so the user sees the actual data at every hop.
 *
 * This is the app's teaching surface: the sequence, and the fact that a client `Success` still has
 * to pass through `Verifying` before it's trusted, is the whole point.
 */
sealed interface PaymentStep {
    val payload: RedactedPayload

    /** Backend created the order; price came from the server. */
    data class OrderCreated(
        val orderId: String,
        val amount: Money,
        override val payload: RedactedPayload,
    ) : PaymentStep

    /** About to hand off to the provider SDK / UPI chooser. Journal row already written. */
    data class Launching(
        val gatewayId: GatewayId,
        override val payload: RedactedPayload = RedactedPayload.EMPTY,
    ) : PaymentStep

    /** The SDK returned a client-side result — a hint, not yet trusted. */
    data class ClientResult(
        val result: PaymentResult,
        override val payload: RedactedPayload,
    ) : PaymentStep

    /** Confirming the client result against the server (signature check / status). */
    data class Verifying(
        override val payload: RedactedPayload = RedactedPayload.EMPTY,
    ) : PaymentStep

    /** Server-authoritative terminal outcome. */
    data class Settled(
        val status: PaymentStatus,
        val snapshot: PaymentSnapshot,
        override val payload: RedactedPayload,
    ) : PaymentStep

    /** The flow itself broke (network, config, unexpected) — distinct from a declined payment. */
    data class Errored(
        val message: UiText,
        override val payload: RedactedPayload = RedactedPayload.EMPTY,
    ) : PaymentStep

    /**
     * One leg of a split payment ([PaymentOrchestrator.paySplit]) reached a terminal state. Wraps
     * the leg's own [Settled] step so the Lab timeline can render "wallet leg settled" / "gateway leg
     * settled" distinctly, while still carrying the same server-authoritative outcome.
     */
    data class LegSettled(
        val leg: SplitLeg,
        val settled: Settled,
        override val payload: RedactedPayload = settled.payload,
    ) : PaymentStep

    /**
     * The wallet leg was reversed because the gateway leg failed (or the split couldn't complete) —
     * the compensating credit from the split-payment design. Net wallet movement is zero.
     */
    data class Compensated(
        val walletAmount: Money,
        val refundTxnId: String,
        override val payload: RedactedPayload,
    ) : PaymentStep
}

/** Which leg of a split payment a [PaymentStep.LegSettled] belongs to. */
enum class SplitLeg { WALLET, GATEWAY }
