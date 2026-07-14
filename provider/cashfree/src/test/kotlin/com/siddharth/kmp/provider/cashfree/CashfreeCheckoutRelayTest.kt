package com.siddharth.kmp.provider.cashfree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [CashfreeCheckoutRelay]. No Android or SDK types touched — this is exactly the
 * single-flight / resume-once bridge logic that the `suspendCancellableCoroutine` in the gateway
 * relies on, so it's the highest-value thing to lock down.
 */
class CashfreeCheckoutRelayTest {
    private val relay = CashfreeCheckoutRelay()

    @Test
    fun verify_forwards_orderId_to_pending_listener() {
        var outcome: CashfreeCheckoutRelay.Outcome? = null
        relay.awaitResult { outcome = it }

        relay.onPaymentVerify("ORDER_1")

        assertTrue(outcome is CashfreeCheckoutRelay.Outcome.Verify)
        assertEquals("ORDER_1", (outcome as CashfreeCheckoutRelay.Outcome.Verify).orderId)
    }

    @Test
    fun failure_forwards_message_and_code() {
        var outcome: CashfreeCheckoutRelay.Outcome? = null
        relay.awaitResult { outcome = it }

        relay.onPaymentFailure("ORDER_2", "card declined", "PAYMENT_DECLINED")

        outcome as CashfreeCheckoutRelay.Outcome.Failure
        assertEquals("ORDER_2", (outcome as CashfreeCheckoutRelay.Outcome.Failure).orderId)
        assertEquals("card declined", (outcome as CashfreeCheckoutRelay.Outcome.Failure).errorMessage)
        assertEquals("PAYMENT_DECLINED", (outcome as CashfreeCheckoutRelay.Outcome.Failure).errorCode)
    }

    @Test
    fun listener_fires_at_most_once_even_on_duplicate_callback() {
        var fireCount = 0
        relay.awaitResult { fireCount++ }

        relay.onPaymentVerify("ORDER_3")
        relay.onPaymentVerify("ORDER_3") // duplicate / late SDK callback
        relay.onPaymentFailure("ORDER_3", "late failure", null)

        assertEquals(1, fireCount)
    }

    @Test
    fun second_awaitResult_while_in_flight_is_rejected() {
        relay.awaitResult { }
        assertThrows(IllegalStateException::class.java) { relay.awaitResult { } }
    }

    @Test
    fun clearPending_lets_a_late_callback_be_dropped() {
        var fired = false
        relay.awaitResult { fired = true }

        relay.clearPending() // simulates coroutine cancellation
        relay.onPaymentVerify("ORDER_4")

        assertTrue(!fired)
    }

    @Test
    fun callback_before_awaitResult_is_a_noop() {
        var outcome: CashfreeCheckoutRelay.Outcome? = null
        // No awaitResult registered yet.
        relay.onPaymentVerify("ORDER_5")

        relay.awaitResult { outcome = it }
        assertNull(outcome)
    }
}
