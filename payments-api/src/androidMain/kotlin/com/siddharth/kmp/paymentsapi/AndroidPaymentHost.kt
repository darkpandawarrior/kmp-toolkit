package com.siddharth.kmp.paymentsapi

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract

/**
 * Android realization of [PaymentHost]. Providers cast the opaque host to this to reach the
 * `ComponentActivity` (some SDKs — Razorpay, Cashfree — require an Activity to open their sheet) and
 * to register ActivityResult contracts (Stripe, UPI intent) without owning the registry themselves.
 *
 * The activity is exposed as a property rather than captured, so providers never retain it past the
 * single [PaymentGateway.pay] call.
 */
interface AndroidPaymentHost : PaymentHost {
    val activity: ComponentActivity

    /**
     * Register a launcher for an ActivityResult contract, valid for the duration of one payment.
     * The host owns registration/unregistration so a provider can't leak it across recreation.
     */
    fun <I, O> registerForResult(
        contract: ActivityResultContract<I, O>,
        onResult: (O) -> Unit,
    ): ActivityResultLauncher<I>
}
