package com.siddharth.kmp.provider.upiintent

import android.content.Intent
import androidx.activity.result.ActivityResult
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PendingReason
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the UPI response → [PaymentResult] mapping. The Android [ActivityResult] /
 * [Intent] pair is mocked (MockK) so the parsing logic is exercised without an emulator.
 */
class UpiIntentGatewayTest {
    private val gateway = UpiIntentGateway()

    private fun resultWith(response: String?): ActivityResult {
        val intent = mockk<Intent>()
        every { intent.getStringExtra("response") } returns response
        val activityResult = mockk<ActivityResult>()
        every { activityResult.data } returns intent
        every { activityResult.resultCode } returns 0
        return activityResult
    }

    @Test
    fun success_maps_to_Success_with_txnId_as_paymentId() {
        val res =
            gateway.parseUpiResult(
                resultWith("txnId=TX123&responseCode=00&Status=SUCCESS&txnRef=REF9"),
            )
        assertTrue(res is PaymentResult.Success)
        res as PaymentResult.Success
        assertEquals("TX123", res.paymentId)
        assertEquals("REF9", res.verification["txnRef"])
        assertEquals("SUCCESS", res.verification["Status"])
    }

    @Test
    fun submitted_maps_to_Pending_UPI_SUBMITTED() {
        val res =
            gateway.parseUpiResult(
                resultWith("txnId=TX1&responseCode=00&Status=SUBMITTED&txnRef=R1"),
            )
        assertTrue(res is PaymentResult.Pending)
        assertEquals(PendingReason.UPI_SUBMITTED, (res as PaymentResult.Pending).reason)
    }

    @Test
    fun failure_maps_to_Failure() {
        val res =
            gateway.parseUpiResult(
                resultWith("txnId=&responseCode=ZM&Status=FAILURE&txnRef="),
            )
        assertTrue(res is PaymentResult.Failure)
    }

    @Test
    fun nullResponse_maps_to_Cancelled() {
        val res = gateway.parseUpiResult(resultWith(null))
        assertTrue(res is PaymentResult.Cancelled)
    }

    @Test
    fun unknownStatus_maps_to_Cancelled_not_falseSuccess() {
        val res = gateway.parseUpiResult(resultWith("Status=WHATEVER"))
        assertTrue(res is PaymentResult.Cancelled)
    }
}
