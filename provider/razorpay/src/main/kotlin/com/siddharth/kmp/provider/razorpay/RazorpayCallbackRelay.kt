package com.siddharth.kmp.provider.razorpay

import com.siddharth.kmp.common.AppLog

/**
 * The result shape Razorpay's Activity-callback SDK produces, normalized so the coroutine bridge and
 * the gateway never touch a Razorpay type (`PaymentData`) directly. The app's Activity flattens
 * `PaymentData` into these fields before forwarding — that keeps the Razorpay dependency confined to
 * the app's listener and out of the gateway's mapping logic.
 */
sealed interface RazorpayCallbackResult {
    /**
     * From Razorpay `onPaymentSuccess(razorpayPaymentId, data)`. The three `razorpay_*` fields are
     * pulled from `PaymentData` (`getOrderId()` / `getPaymentId()` / `getSignature()`); [extra] is
     * any remaining non-secret display material from `PaymentData.getData()`.
     */
    data class Success(
        val razorpayPaymentId: String?,
        val razorpayOrderId: String?,
        val razorpaySignature: String?,
        val extra: Map<String, String> = emptyMap(),
    ) : RazorpayCallbackResult

    /** From Razorpay `onPaymentError(code, description, data)`. */
    data class Error(
        val code: Int,
        val description: String?,
        val extra: Map<String, String> = emptyMap(),
    ) : RazorpayCallbackResult
}

/**
 * The contract the app's Activity must satisfy so it can forward Razorpay callbacks into this
 * module. The app's `MainActivity` implements Razorpay's `PaymentResultWithDataListener` and, from
 * `onPaymentSuccess` / `onPaymentError`, calls [RazorpayCallbackRelay.emit].
 *
 * This interface documents the *intent* of that wiring; the relay below is the concrete channel.
 */
interface PaymentActivityCallbacks {
    fun onRazorpayResult(result: RazorpayCallbackResult)
}

/**
 * Process-scoped, single-slot holder that bridges Razorpay's Activity-level callback back to the
 * suspended coroutine in [RazorpayGateway.pay].
 *
 * WHY A PROCESS-SCOPED RELAY: `Checkout.open(activity, options)` requires the *Activity* to
 * implement `com.razorpay.PaymentResultWithDataListener` — the SDK has no per-call callback
 * parameter. Our architecture keeps Activity references out of gateways, so the app's single
 * `MainActivity` owns the Razorpay listener and forwards each result here. The gateway registers a
 * one-shot [listener] for the duration of one `pay()` call and clears it on completion.
 *
 * Concurrency note: only one Razorpay checkout can be on screen at a time (it's a full Activity
 * sheet), so a single-slot listener is correct. A second registration while one is pending replaces
 * the stale slot and logs a warning rather than silently dropping the new one.
 */
object RazorpayCallbackRelay : PaymentActivityCallbacks {
    @Volatile
    private var listener: ((RazorpayCallbackResult) -> Unit)? = null

    /** Called by the gateway before `Checkout.open`. Returns a handle to clear the slot afterwards. */
    fun register(onResult: (RazorpayCallbackResult) -> Unit) {
        if (listener != null) {
            AppLog.w("overwriting a still-pending Razorpay listener", tag = TAG)
        }
        listener = onResult
    }

    fun clear() {
        listener = null
    }

    /** Called by the app's Activity from Razorpay's onPaymentSuccess/onPaymentError. */
    override fun onRazorpayResult(result: RazorpayCallbackResult) = emit(result)

    fun emit(result: RazorpayCallbackResult) {
        val current = listener
        if (current == null) {
            AppLog.w("Razorpay result arrived with no registered listener (dropped): $result", tag = TAG)
            return
        }
        current.invoke(result)
    }

    private const val TAG = "RazorpayCallbackRelay"
}
