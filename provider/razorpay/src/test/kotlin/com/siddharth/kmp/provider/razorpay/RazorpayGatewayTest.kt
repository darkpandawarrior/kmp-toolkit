package com.siddharth.kmp.provider.razorpay

import com.siddharth.kmp.paymentsapi.PaymentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the Razorpay callback → [PaymentResult] mapping. These exercise the
 * success/redaction path, which does not touch the Android `Checkout` constants (error-code mapping
 * that references `Checkout.*` is covered by instrumented tests to avoid loading the SDK class here).
 */
class RazorpayGatewayTest {
    private val gateway = RazorpayGateway(RazorpayCallbackRelay)

    @Test
    fun success_puts_signature_in_verification_unredacted() {
        val res =
            gateway.mapCallback(
                RazorpayCallbackResult.Success(
                    razorpayPaymentId = "pay_ABC123",
                    razorpayOrderId = "order_XYZ789",
                    razorpaySignature = "abcdef0123456789signaturevalue",
                ),
            )
        assertTrue(res is PaymentResult.Success)
        res as PaymentResult.Success
        assertEquals("pay_ABC123", res.paymentId)
        // verification carries the signature INTACT for the server
        assertEquals("abcdef0123456789signaturevalue", res.verification["razorpay_signature"])
        assertEquals("order_XYZ789", res.verification["razorpay_order_id"])
    }

    @Test
    fun success_masks_signature_in_raw() {
        val res =
            gateway.mapCallback(
                RazorpayCallbackResult.Success(
                    razorpayPaymentId = "pay_ABC123",
                    razorpayOrderId = "order_XYZ789",
                    razorpaySignature = "abcdef0123456789signaturevalue",
                ),
            ) as PaymentResult.Success

        val rawSig =
            res.raw.entries
                .first { it.first == "razorpay_signature" }
                .second
        // masked (contains the redaction bullet), NOT the plaintext signature
        assertTrue("expected masked signature, was '$rawSig'", rawSig.contains("•"))
        assertTrue(rawSig != "abcdef0123456789signaturevalue")
    }
}
// NB: the Error path (RazorpayCallbackResult.Error → PaymentResult.Failure) exercises
// mapErrorCode(), whose `when` branches reference android `Checkout.*` constants. Loading
// `com.razorpay.Checkout` (extends android.app.Fragment) requires the Android runtime, so that
// path is left to instrumented tests rather than this pure-JVM suite.
