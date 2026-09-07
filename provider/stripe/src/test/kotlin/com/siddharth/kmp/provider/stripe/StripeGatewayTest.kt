package com.siddharth.kmp.provider.stripe

import com.siddharth.kmp.paymentsapi.CreatedOrder
import com.siddharth.kmp.paymentsapi.FailureCode
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.OrderRef
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentPreparationException
import com.siddharth.kmp.paymentsapi.PaymentResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the parts of [StripeGateway] that don't require a real Activity or the Stripe SDK (the
 * actual `PaymentSheet` presentation can't be unit-tested without an emulator) — same shape as
 * [com.siddharth.kmp.provider.square.SquareGatewayTest]'s "no Activity needed" split. `pay()` is
 * reachable here because passing a non-[com.siddharth.kmp.paymentsapi.AndroidPaymentHost] makes it
 * return `CONFIG_MISSING` before it ever touches `PaymentConfiguration`/`PaymentSheet`.
 */
class StripeGatewayTest {
    // Never actually presented: `pay()` bails out with CONFIG_MISSING before it calls
    // `launcherHost.present(...)`, because `FakePaymentHost` below is not an `AndroidPaymentHost`.
    private val gateway = StripeGateway(StripePaymentLauncherHost())
    private val orderRef = OrderRef(orderId = "order_1", catalogItemId = "coffee_149", amount = Money.usd(149))

    private fun createdOrder(providerParams: Map<String, String>) =
        CreatedOrder(order = orderRef, gatewayId = GatewayId("stripe"), providerParams = providerParams)

    @Test
    fun `prepare passes client secret and publishable key through`() =
        runTest {
            val prepared =
                gateway.prepare(
                    createdOrder(mapOf("client_secret" to "pi_order_1_secret_demo", "publishable_key" to "pk_test_fake")),
                )

            assertEquals("pi_order_1_secret_demo", prepared.params["client_secret"])
            assertEquals("pk_test_fake", prepared.params["publishable_key"])
        }

    @Test(expected = PaymentPreparationException::class)
    fun `prepare throws when client_secret is missing`() =
        runTest {
            gateway.prepare(createdOrder(mapOf("publishable_key" to "pk_test_fake")))
        }

    @Test(expected = PaymentPreparationException::class)
    fun `prepare throws when publishable_key is missing`() =
        runTest {
            gateway.prepare(createdOrder(mapOf("client_secret" to "pi_order_1_secret_demo")))
        }

    @Test
    fun `pay fails with CONFIG_MISSING when the host is not an AndroidPaymentHost`() =
        runTest {
            val prepared =
                gateway.prepare(
                    createdOrder(mapOf("client_secret" to "pi_order_1_secret_demo", "publishable_key" to "pk_test_fake")),
                )

            val result = gateway.pay(host = FakePaymentHost, prepared = prepared)

            assertTrue(result is PaymentResult.Failure)
            assertEquals(FailureCode.CONFIG_MISSING, (result as PaymentResult.Failure).code)
        }

    private object FakePaymentHost : PaymentHost
}
