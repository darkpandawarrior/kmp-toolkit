package com.siddharth.kmp.provider.stripeconnect.di

import com.siddharth.kmp.provider.stripeconnect.StripeConnectOnboarding
import org.koin.dsl.module

/**
 * Koin module for `provider:stripe-connect`. [com.siddharth.kmp.provider.hostedwebview.HostedCheckoutRelay]
 * is already a Koin single contributed by `hostedWebViewModule` — this resolves the same instance via
 * `get()` (same reuse [com.siddharth.kmp.provider.paystack.di.paystackModule] does) rather than a
 * second relay instance.
 */
val stripeConnectModule =
    module {
        single { StripeConnectOnboarding(get(), get()) }
    }
