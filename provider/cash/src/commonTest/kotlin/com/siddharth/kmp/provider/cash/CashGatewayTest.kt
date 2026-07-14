package com.siddharth.kmp.provider.cash

import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PendingReason
import com.siddharth.kmp.paymentsapi.PreparedPayment
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CashGatewayTest {
    private val prepared =
        PreparedPayment(
            gatewayId = GatewayId("cash"),
            orderId = "order_1",
            amount = Money.inr(100),
            params = emptyMap(),
        )

    @Test
    fun `pay returns Pending with no network call - record-only`() =
        runTest {
            val gateway = CashGateway()

            val result = gateway.pay(host = FakeHost, prepared = prepared)

            val pending = assertIs<PaymentResult.Pending>(result)
            assertEquals(PendingReason.AWAITING_WEBHOOK, pending.reason)
        }

    @Test
    fun `meta is honest about record-only mock mode`() {
        val gateway = CashGateway()

        assertEquals("Cash", gateway.meta.displayName)
        assertEquals(GatewayStatus.MOCK_MODE, gateway.meta.status)
        assertEquals(GatewayId("cash"), gateway.id)
    }

    private object FakeHost : PaymentHost
}
