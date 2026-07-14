package com.siddharth.kmp.paymentsapi

import kotlin.jvm.JvmInline

/** Stable identifier for a payment provider, e.g. `razorpay`, `upi_intent`, `stripe`. */
@JvmInline
value class GatewayId(
    val value: String,
)

/** Feature a gateway supports — drives capability-filtered lookups and Lab badges. */
enum class Capability {
    ONE_TIME_PAYMENT,
    UPI,
    CARDS,
    WALLET,
    NET_BANKING,
    REFUND,
    MANDATE,
}

/**
 * Whether a provider can be exercised end-to-end in this showcase.
 *
 * The whole app is honest about this: KYC/partner-gated providers appear in the catalog with
 * documentation but cannot be run in sandbox by a solo developer.
 */
enum class GatewayStatus {
    /** Fully runnable in sandbox with no business KYC. */
    SANDBOX_READY,

    /**
     * Real integration code, but no solo-accessible sandbox — the full lifecycle runs against a
     * mock backend so the Lab is demoable end-to-end without live credentials.
     */
    MOCK_MODE,

    /** Requires business onboarding / partner credentials — catalog + docs only. */
    KYC_GATED,

    /** Planned, not yet implemented. */
    COMING_SOON,
}

/** Catalog metadata for a gateway — everything the Lab home needs without touching the impl. */
data class GatewayMeta(
    val displayName: String,
    val status: GatewayStatus,
    val capabilities: Set<Capability>,
    val region: String,
    val docsPath: String,
    val blurb: String,
)

/**
 * The single contract every payment provider implements. Deliberately tiny and platform-agnostic:
 * the messy, Activity-callback-era SDK reality is confined to each provider's androidMain impl and
 * bridged back into a coroutine by [PaymentHost].
 *
 * [prepare] does the server round-trip (order creation → session token); [pay] launches the SDK/UI
 * and suspends until a terminal [PaymentResult]. Splitting them keeps the network step testable and
 * the launch step host-driven.
 */
interface PaymentGateway {
    val id: GatewayId
    val meta: GatewayMeta

    /**
     * Transform a backend-created order into this provider's session shape. The network round-trip
     * already happened (the orchestrator owns [PaymentBackend]); most providers just repackage
     * [CreatedOrder.providerParams], but the call is suspend for providers that need an extra hop.
     * Throws [PaymentPreparationException] on failure.
     */
    suspend fun prepare(created: CreatedOrder): PreparedPayment

    /** Launch the provider UI/intent and suspend until the user reaches a terminal state. */
    suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult
}

/** Thrown by [PaymentGateway.prepare] when the order/session could not be created. */
class PaymentPreparationException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)
