package com.siddharth.kmp.provider.stripe

import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

/**
 * The app-side bridge between Stripe's Compose/Activity-scoped [PaymentSheet] and the coroutine that
 * [StripeGateway.pay] suspends on.
 *
 * Why this exists: `PaymentSheet` must be created inside an `Activity`/Compose scope (via
 * `PaymentSheet.Builder(resultCallback).build(activity)` or `rememberPaymentSheet { }`), because the
 * SDK registers an `ActivityResultLauncher` under the hood and that registration is only legal before
 * the host reaches `STARTED`. A provider running deep inside a suspend function is far too late to do
 * that. So the app owns the [PaymentSheet]; the gateway owns nothing but this relay.
 *
 * Contract:
 *  - The app creates one [PaymentSheet] whose `resultCallback` forwards to [onResult]. It hands the
 *    presenter (usually `paymentSheet::presentWithPaymentIntent`) to the host via [attach].
 *  - [StripeGateway] calls [present] with the client secret + config; the SDK later fires the
 *    callback, which the app routes back through [onResult]; the host resumes the awaiting coroutine.
 *
 * Single-owner, single-flight: only one payment can be in flight at a time. [present] fails fast if
 * a result listener is already registered, and [onResult] clears the listener after firing so a
 * duplicated SDK callback (or a late one after cancellation) can never resume twice.
 */
class StripePaymentLauncherHost {
    /** How to present the sheet, supplied by the app once the [PaymentSheet] is built. */
    fun interface Presenter {
        fun present(
            clientSecret: String,
            configuration: PaymentSheet.Configuration,
        )
    }

    private var presenter: Presenter? = null

    // Guarded by @Synchronized so the SDK callback thread and the calling coroutine can't race on it.
    private var pendingResult: ((PaymentSheetResult) -> Unit)? = null

    /** App-side wiring: register how to present the sheet. Call once, before any payment. */
    fun attach(presenter: Presenter) {
        this.presenter = presenter
    }

    /** App-side wiring: drop the presenter (e.g. on Activity/composable disposal). */
    fun detach() {
        this.presenter = null
    }

    /**
     * Present the sheet and register the one-shot result listener. Called by [StripeGateway.pay].
     * @throws IllegalStateException if no presenter is attached or a payment is already in flight.
     */
    @Synchronized
    fun present(
        clientSecret: String,
        configuration: PaymentSheet.Configuration,
        onResult: (PaymentSheetResult) -> Unit,
    ) {
        val activePresenter =
            presenter
                ?: error("No PaymentSheet attached — the app must call attach() before launching Stripe.")
        check(pendingResult == null) { "A Stripe payment is already in flight." }
        pendingResult = onResult
        activePresenter.present(clientSecret, configuration)
    }

    /**
     * App-side wiring: the [PaymentSheet] `resultCallback` calls this. Fires the pending listener
     * exactly once and clears it, so a duplicate/late SDK callback is a no-op.
     */
    @Synchronized
    fun onResult(result: PaymentSheetResult) {
        val listener = pendingResult ?: return
        pendingResult = null
        listener(result)
    }

    /** Drop a pending listener without firing it — used when the awaiting coroutine is cancelled. */
    @Synchronized
    fun clearPending() {
        pendingResult = null
    }
}
