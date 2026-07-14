package com.siddharth.kmp.paymentsapi

import com.siddharth.kmp.common.UiText

/** Everything a Tier-4 catalog-only entry needs: identity + the research-backed blurb/docs link. */
data class StubGatewayConfig(
    val id: GatewayId,
    val displayName: String,
    val region: String,
    val docsPath: String,
    val blurb: String,
    val capabilities: Set<Capability> = setOf(Capability.ONE_TIME_PAYMENT),
)

/**
 * A Tier-4 "stub/docs-only" catalog entry: real enough to appear in the Lab list with an honest
 * [GatewayStatus.COMING_SOON] badge and a web-researched doc, but with no working integration behind
 * it (no backend adapter, no real or mock SDK call). `pay` always fails — nothing should ever
 * actually invoke it in normal use since the UI badges it as not-yet-available, but the contract
 * still has to return *something* rather than throw if it somehow is.
 */
class StubGateway(
    private val config: StubGatewayConfig,
) : PaymentGateway {
    override val id: GatewayId = config.id

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = config.displayName,
            status = GatewayStatus.COMING_SOON,
            capabilities = config.capabilities,
            region = config.region,
            docsPath = config.docsPath,
            blurb = config.blurb,
        )

    override suspend fun prepare(created: CreatedOrder): PreparedPayment =
        PreparedPayment(
            gatewayId = id,
            orderId = created.order.orderId,
            amount = created.order.amount,
            params = emptyMap(),
        )

    override suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult =
        PaymentResult.Failure(
            code = FailureCode.SDK_ERROR,
            message = UiText.of("${config.displayName} integration is coming soon — not yet implemented"),
            raw = Redactor.redact("${id.value}_stub", emptyMap()),
        )
}
