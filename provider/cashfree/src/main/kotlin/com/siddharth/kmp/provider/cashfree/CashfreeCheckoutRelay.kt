package com.siddharth.kmp.provider.cashfree

import com.siddharth.kmp.common.AppLog

/**
 * Bridges Cashfree's Activity-scoped `CFCheckoutResponseCallback` into the coroutine that
 * [CashfreeGateway.pay] suspends on.
 *
 * Why this exists: `CFPaymentGatewayService.getInstance().setCheckoutCallback(...)` must be called in
 * the host Activity's `onCreate` — the SDK explicitly requires this so the callback survives Activity
 * recreation while the checkout screen is up. A provider inside a suspend function is far too late
 * (and would leak the Activity). So the app registers a `CFCheckoutResponseCallback` in `onCreate`
 * that forwards to [onPaymentVerify] / [onPaymentFailure] here, and the gateway registers a one-shot
 * listener via [awaitResult] before calling `doPayment`.
 *
 * Single-flight + resume-once: only one payment is in flight at a time. [awaitResult] rejects a
 * second registration; firing either terminal clears the listener, so a duplicated or late SDK
 * callback can't resume the coroutine twice.
 */
class CashfreeCheckoutRelay {
    /** Terminal outcome the SDK reports, normalized to a tiny sealed type the gateway maps. */
    sealed interface Outcome {
        data class Verify(
            val orderId: String,
        ) : Outcome

        data class Failure(
            val orderId: String,
            val errorMessage: String,
            val errorCode: String?,
        ) : Outcome
    }

    // Guarded by @Synchronized: the SDK callback thread and the calling coroutine race on this.
    private var pending: ((Outcome) -> Unit)? = null

    /**
     * Register the one-shot listener for the next terminal callback. Called by the gateway right
     * before `doPayment`.
     * @throws IllegalStateException if a payment is already in flight.
     */
    @Synchronized
    fun awaitResult(onOutcome: (Outcome) -> Unit) {
        check(pending == null) { "A Cashfree payment is already in flight." }
        pending = onOutcome
    }

    /** App-side wiring: the `CFCheckoutResponseCallback.onPaymentVerify` forwards here. */
    @Synchronized
    fun onPaymentVerify(orderId: String) {
        AppLog.i("onPaymentVerify order=$orderId", tag = TAG)
        fire(Outcome.Verify(orderId))
    }

    /** App-side wiring: the `CFCheckoutResponseCallback.onPaymentFailure` forwards here. */
    @Synchronized
    fun onPaymentFailure(
        orderId: String,
        errorMessage: String,
        errorCode: String?,
    ) {
        AppLog.w("onPaymentFailure order=$orderId message=$errorMessage code=$errorCode", tag = TAG)
        fire(Outcome.Failure(orderId, errorMessage, errorCode))
    }

    /** Drop a pending listener without firing — used when the awaiting coroutine is cancelled. */
    @Synchronized
    fun clearPending() {
        pending = null
    }

    private fun fire(outcome: Outcome) {
        val listener = pending ?: return
        pending = null
        listener(outcome)
    }

    private companion object {
        const val TAG = "CashfreeRelay"
    }
}
