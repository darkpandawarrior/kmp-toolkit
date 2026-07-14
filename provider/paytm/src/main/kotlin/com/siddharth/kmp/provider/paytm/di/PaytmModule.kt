package com.siddharth.kmp.provider.paytm.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.hostedwebview.HostedCheckoutRelay
import com.siddharth.kmp.provider.paytm.PaytmGateway
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module for `provider:paytm`.
 *
 * [HostedCheckoutRelay] is already a Koin single contributed by `hostedWebViewModule` — this module
 * resolves the same instance via `get()` rather than creating a second relay, so Paytm's checkout
 * shares the one `HostedCheckoutHost` composable mounted by the consuming app with every other
 * hosted-checkout gateway.
 */
val paytmModule: Module =
    module {
        single { PaytmGateway(get()) } bind PaymentGateway::class
    }
