package com.siddharth.kmp.provider.square

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
 * Covers the parts of [SquareGateway] that don't require an Activity or the real SDK (the actual
 * `CardEntryActivity` launch can't be unit-tested without an emulator).
 */
class SquareGatewayTest {
    private val gateway = SquareGateway()
    private val orderRef = OrderRef(orderId = "order_1", catalogItemId = "coffee_149", amount = Money.usd(149))

    private fun createdOrder(providerParams: Map<String, String>) =
        CreatedOrder(order = orderRef, gatewayId = GatewayId("square"), providerParams = providerParams)

    @Test
    fun `prepare passes the application id through when the backend provided one`() =
        runTest {
            val prepared = gateway.prepare(createdOrder(mapOf("application_id" to "sandbox-sq0idb-fake")))

            assertEquals("sandbox-sq0idb-fake", prepared.params["application_id"])
        }

    @Test
    fun `prepare omits the application id when the backend is in mock mode`() =
        runTest {
            val prepared = gateway.prepare(createdOrder(emptyMap()))

            assertTrue(prepared.params.isEmpty())
        }

    @Test
    fun `pay falls back to a simulated result when no application id was prepared`() =
        runTest {
            val prepared = gateway.prepare(createdOrder(emptyMap()))

            val result = gateway.pay(host = FakePaymentHost, prepared = prepared)

            assertTrue(result is PaymentResult.Success)
        }

    private object FakePaymentHost : PaymentHost
}
