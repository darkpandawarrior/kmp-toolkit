package com.siddharth.kmp.provider.razorpay.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.razorpay.PaymentActivityCallbacks
import com.siddharth.kmp.provider.razorpay.RazorpayCallbackRelay
import com.siddharth.kmp.provider.razorpay.RazorpayGateway
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module for the Razorpay provider.
 *
 * The gateway is registered as its concrete [RazorpayGateway] and bound to the [PaymentGateway]
 * supertype via `bind`, so `getAll<PaymentGateway>()` collects it alongside every other provider
 * without a definition override.
 *
 * The process-scoped [RazorpayCallbackRelay] is provided both as itself (so the gateway can inject
 * it via `get()`) and bound to [PaymentActivityCallbacks] — the app's `MainActivity` resolves the
 * latter to forward Razorpay's Activity callbacks into the coroutine bridge.
 */
val razorpayModule: Module =
    module {
        single { RazorpayCallbackRelay } bind PaymentActivityCallbacks::class
        single { RazorpayGateway(get()) } bind PaymentGateway::class
    }
