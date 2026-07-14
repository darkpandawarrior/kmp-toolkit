package com.siddharth.kmp.paymentsapi

/**
 * The collected set of gateways available in the app. Each `provider:*` module contributes one
 * [PaymentGateway] into DI; the app assembles them here. Features depend only on this registry, so
 * adding provider N+1 touches no existing feature code.
 */
interface PaymentGatewayRegistry {
    val gateways: List<PaymentGateway>

    fun byId(id: GatewayId): PaymentGateway?

    fun withCapability(capability: Capability): List<PaymentGateway>
}

class DefaultPaymentGatewayRegistry(
    override val gateways: List<PaymentGateway>,
) : PaymentGatewayRegistry {
    private val byId: Map<GatewayId, PaymentGateway> = gateways.associateBy { it.id }

    override fun byId(id: GatewayId): PaymentGateway? = byId[id]

    override fun withCapability(capability: Capability): List<PaymentGateway> =
        gateways.filter { capability in it.meta.capabilities }
}
