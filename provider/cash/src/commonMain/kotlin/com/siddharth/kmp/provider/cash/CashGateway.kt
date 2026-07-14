package com.siddharth.kmp.provider.cash

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.CreatedOrder
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayMeta
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PendingReason
import com.siddharth.kmp.paymentsapi.PreparedPayment
import com.siddharth.kmp.paymentsapi.Redactor

/**
 * Archetype-D-simplest: a record-only gateway for cash collected outside the app entirely (courier
 * COD, in-person handoff). Unlike mobile-money, there is no provider to even ping — `pay()` does
 * nothing but record intent and return `Pending`; the order is only ever resolved when a human
 * (merchant/cashier) confirms the cash was received, via the backend's `/mock/cash/{orderId}/settle`
 * reconciliation route. No SDK, no WebView, no network call from this gateway at all.
 */
class CashGateway : PaymentGateway {
    override val id: GatewayId = GatewayId("cash")

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = "Cash",
            status = GatewayStatus.MOCK_MODE,
            capabilities = setOf(Capability.ONE_TIME_PAYMENT),
            region = "Global",
            docsPath = "docs/providers/cash.md",
            blurb = "Record-only — settled manually once cash is physically received, no SDK/webhook.",
        )

    override suspend fun prepare(created: CreatedOrder): PreparedPayment =
        PreparedPayment(
            gatewayId = id,
            orderId = created.order.orderId,
            amount = created.order.amount,
            params = created.providerParams,
        )

    override suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult =
        PaymentResult.Pending(
            reason = PendingReason.AWAITING_WEBHOOK,
            raw = Redactor.redact("cash_pending", mapOf("order_id" to prepared.orderId, "mode" to "manual_settlement")),
        )
}
