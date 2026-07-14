package com.siddharth.kmp.provider.mobilemoney

import com.siddharth.kmp.network.createHttpClient
import com.siddharth.kmp.paymentsapi.Capability
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

class MobileMoneyGatewayTest {
    private val config =
        MobileMoneyConfig(
            gatewayId = GatewayId("mock_momo"),
            displayName = "Mock MoMo",
            region = "Africa",
            docsPath = "docs/providers/mock-momo.md",
            blurb = "test",
            capabilities = setOf(Capability.ONE_TIME_PAYMENT),
            status = GatewayStatus.MOCK_MODE,
        )
    private val prepared =
        PreparedPayment(
            gatewayId = GatewayId("mock_momo"),
            orderId = "order_1",
            amount = Money.inr(100),
            params = emptyMap(),
        )

    /**
     * Points the shared client at an unreachable host so the mock-flip POST fails fast — this
     * verifies `pay()` degrades gracefully (still returns Pending) rather than propagating the
     * network failure, since the flip request is best-effort, not load-bearing for the FSM's own
     * polling to eventually resolve the order via a real webhook too.
     */
    @Test
    fun `pay returns Pending even if the mock momo trigger request fails`() =
        runTest {
            val httpClient = createHttpClient()
            val gateway = MobileMoneyGateway(config, httpClient, PaymentApiConfig(baseUrl = "http://127.0.0.1:1"))

            val result = gateway.pay(host = FakeHost, prepared = prepared)

            val pending = assertIs<PaymentResult.Pending>(result)
            assertEquals(PendingReason.AWAITING_WEBHOOK, pending.reason)
        }

    @Test
    fun `meta is built from config`() {
        val httpClient = createHttpClient()
        val gateway = MobileMoneyGateway(config, httpClient, PaymentApiConfig())

        assertEquals("Mock MoMo", gateway.meta.displayName)
        assertEquals(GatewayStatus.MOCK_MODE, gateway.meta.status)
        assertEquals(GatewayId("mock_momo"), gateway.id)
    }

    private object FakeHost : com.siddharth.kmp.paymentsapi.PaymentHost
}
