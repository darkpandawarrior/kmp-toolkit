package com.siddharth.kmp.provider.omise

import com.siddharth.kmp.paymentsapi.CreatedOrder
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.OrderRef
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the parts of [OmiseGateway] that don't require an Activity or the real SDK (the actual
 * `CreditCardActivity` launch can't be unit-tested without an emulator).
 */
class OmiseGatewayTest {
    private val gateway = OmiseGateway()
    private val orderRef = OrderRef(orderId = "order_1", catalogItemId = "coffee_149", amount = Money.usd(149))

    private fun createdOrder(providerParams: Map<String, String>) =
        CreatedOrder(order = orderRef, gatewayId = GatewayId("omise"), providerParams = providerParams)

    @Test
    fun `prepare passes the public key through when the backend provided one`() =
        runTest {
            val prepared = gateway.prepare(createdOrder(mapOf("public_key" to "pkey_test_fake")))

            assertEquals("pkey_test_fake", prepared.params["public_key"])
        }

    @Test
    fun `prepare omits the public key when the backend is in mock mode`() =
        runTest {
            val prepared = gateway.prepare(createdOrder(emptyMap()))

            assertTrue(prepared.params.isEmpty())
        }

    @Test
    fun `pay falls back to a simulated result when no public key was prepared`() =
        runTest {
            val prepared = gateway.prepare(createdOrder(emptyMap()))

            val result = gateway.pay(host = FakePaymentHost, prepared = prepared)

            assertTrue(result is PaymentResult.Success)
        }

    private object FakePaymentHost : PaymentHost
}
