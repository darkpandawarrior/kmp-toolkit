package com.siddharth.kmp.provider.nmi

import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.InstrumentCharge
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PaymentStatus
import com.siddharth.kmp.paymentsapi.PreparedPayment
import com.siddharth.kmp.paymentsapi.SavedInstrument
import com.siddharth.kmp.paymentsapi.VaultBackend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NmiGatewayTest {
    private fun prepared(params: Map<String, String>) =
        PreparedPayment(
            gatewayId = GatewayId("nmi"),
            orderId = "order_1",
            amount = Money.inr(100),
            params = params,
        )

    @Test
    fun `pay charges the saved instrument via the shared vault`() =
        runTest {
            val gateway = NmiGateway(FakeVaultBackend())

            val result =
                gateway.pay(
                    host = FakeHost,
                    prepared =
                        prepared(
                            mapOf(
                                "customerId" to "cust_1",
                                "instrumentId" to "instr_1",
                                "catalogItemId" to "item_1",
                            ),
                        ),
                )

            val success = assertIs<PaymentResult.Success>(result)
            assertEquals("charge_fake", success.paymentId)
        }

    @Test
    fun `pay fails cleanly when instrument params are missing`() =
        runTest {
            val gateway = NmiGateway(FakeVaultBackend())

            val result = gateway.pay(host = FakeHost, prepared = prepared(emptyMap()))

            assertIs<PaymentResult.Failure>(result)
        }

    @Test
    fun `meta advertises the vault-pattern capability honestly`() {
        val gateway = NmiGateway(FakeVaultBackend())

        assertEquals("NMI", gateway.meta.displayName)
        assertEquals(GatewayStatus.MOCK_MODE, gateway.meta.status)
        assertEquals(GatewayId("nmi"), gateway.id)
    }

    private object FakeHost : PaymentHost

    private class FakeVaultBackend : VaultBackend {
        override suspend fun save(
            customerId: String,
            cardToken: String,
            brand: String,
            last4: String,
            idempotencyKey: String,
        ): SavedInstrument = SavedInstrument("instr_fake", customerId, brand, last4)

        override suspend fun list(customerId: String): List<SavedInstrument> = emptyList()

        override suspend fun charge(
            customerId: String,
            instrumentId: String,
            catalogItemId: String,
            idempotencyKey: String,
        ): InstrumentCharge =
            InstrumentCharge(
                chargeId = "charge_fake",
                customerId = customerId,
                instrumentId = instrumentId,
                amount = Money.inr(100),
                status = PaymentStatus.SUCCESS,
            )
    }
}
