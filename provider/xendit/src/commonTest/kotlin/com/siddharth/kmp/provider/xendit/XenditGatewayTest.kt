package com.siddharth.kmp.provider.xendit

import com.siddharth.kmp.network.createHttpClient
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.PaymentApiConfig
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PendingReason
import com.siddharth.kmp.paymentsapi.PreparedPayment
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class XenditGatewayTest {
    private val config = XenditConfig()
    private val prepared =
        PreparedPayment(
            gatewayId = GatewayId("xendit"),
            orderId = "order_1",
            amount = Money.inr(100),
            params = emptyMap(),
        )

    /** Mirrors MobileMoneyGatewayTest: unreachable host so the mock-flip POST fails fast. */
    @Test
    fun `pay returns Pending even if the mock xendit trigger request fails`() =
        runTest {
            val httpClient = createHttpClient()
            val gateway = XenditGateway(config, httpClient, PaymentApiConfig(baseUrl = "http://127.0.0.1:1"))

            val result = gateway.pay(host = FakeHost, prepared = prepared)

            val pending = assertIs<PaymentResult.Pending>(result)
            assertEquals(PendingReason.AWAITING_WEBHOOK, pending.reason)
        }

    @Test
    fun `meta is built from config`() {
        val httpClient = createHttpClient()
        val gateway = XenditGateway(config, httpClient, PaymentApiConfig())

        assertEquals("Xendit", gateway.meta.displayName)
        assertEquals(GatewayStatus.MOCK_MODE, gateway.meta.status)
        assertEquals(GatewayId("xendit"), gateway.id)
    }

    private object FakeHost : com.siddharth.kmp.paymentsapi.PaymentHost
}
