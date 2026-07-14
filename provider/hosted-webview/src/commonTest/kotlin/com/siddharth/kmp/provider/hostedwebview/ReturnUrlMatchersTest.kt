package com.siddharth.kmp.provider.hostedwebview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReturnUrlMatchersTest {
    private val matcher =
        ReturnUrlMatchers.byMarker(successMarker = "/return/success", failureMarker = "/return/failure")

    @Test
    fun `success marker extracts payment id`() {
        val outcome = matcher("https://checkout.example/return/success?payment_id=abc123")

        assertEquals("abc123", assertIs<HostedReturnOutcome.Success>(outcome).paymentId)
    }

    @Test
    fun `failure marker extracts reason`() {
        val outcome = matcher("https://checkout.example/return/failure?reason=card_declined")

        assertEquals("card_declined", assertIs<HostedReturnOutcome.Failure>(outcome).reason)
    }

    @Test
    fun `a mid-flow url is not yet terminal`() {
        assertNull(matcher("https://checkout.example/pay?step=otp"))
    }
}
