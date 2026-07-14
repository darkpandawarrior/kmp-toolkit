package com.siddharth.kmp.paymentsapi

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SimulatedPaymentTest {
    private val prepared =
        PreparedPayment(
            gatewayId = GatewayId("mock_gw"),
            orderId = "order_1",
            amount = Money.inr(10),
            params = mapOf("key_id" to "secret_value"),
        )

    @Test
    fun `success outcome carries a redacted mock payload`() =
        runTest {
            val result = SimulatedPayment.run(GatewayId("mock_gw"), prepared, SimulatedOutcome.SUCCESS, delayMs = 0)

            val success = assertIs<PaymentResult.Success>(result)
            assertEquals("mock_pay_order_1", success.paymentId)
            assertEquals("succeeded", success.verification["marker"])
            // key_id looks like a secret to Redactor and must be masked, not dropped or shown raw.
            assertEquals("MOCK_MODE", success.raw.entries.toMap()["mode"])
            assertEquals(
                true,
                success.raw.entries
                    .toMap()["key_id"]
                    ?.contains("•"),
            )
        }

    @Test
    fun `failure outcome maps to gateway declined`() =
        runTest {
            val result = SimulatedPayment.run(GatewayId("mock_gw"), prepared, SimulatedOutcome.FAILURE, delayMs = 0)

            assertEquals(FailureCode.GATEWAY_DECLINED, assertIs<PaymentResult.Failure>(result).code)
        }

    @Test
    fun `pending outcome awaits webhook`() =
        runTest {
            val result = SimulatedPayment.run(GatewayId("mock_gw"), prepared, SimulatedOutcome.PENDING, delayMs = 0)

            assertEquals(PendingReason.AWAITING_WEBHOOK, assertIs<PaymentResult.Pending>(result).reason)
        }
}
