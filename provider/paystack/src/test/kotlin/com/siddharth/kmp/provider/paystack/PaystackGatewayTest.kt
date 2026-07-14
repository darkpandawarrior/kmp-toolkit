package com.siddharth.kmp.provider.paystack

import com.siddharth.kmp.paymentsapi.FailureCode
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.provider.hostedwebview.HostedCheckoutRelay
import com.siddharth.kmp.provider.hostedwebview.HostedReturnOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the Paystack return-URL outcome → [PaymentResult] mapping. */
class PaystackGatewayTest {
    private val gateway = PaystackGateway(HostedCheckoutRelay())

    @Test
    fun success_maps_payment_id_into_verification() {
        val res = gateway.mapOutcome(HostedReturnOutcome.Success(paymentId = "mock_pay_order_1"))
        assertTrue(res is PaymentResult.Success)
        res as PaymentResult.Success
        assertEquals("mock_pay_order_1", res.paymentId)
        assertEquals("mock_pay_order_1", res.verification["payment_id"])
    }

    @Test
    fun failure_maps_reason_to_gateway_declined() {
        val res = gateway.mapOutcome(HostedReturnOutcome.Failure(reason = "card_declined"))
        assertTrue(res is PaymentResult.Failure)
        res as PaymentResult.Failure
        assertEquals(FailureCode.GATEWAY_DECLINED, res.code)
    }

    @Test
    fun cancelled_maps_to_cancelled_result() {
        val res = gateway.mapOutcome(HostedReturnOutcome.Cancelled)
        assertTrue(res is PaymentResult.Cancelled)
    }
}
