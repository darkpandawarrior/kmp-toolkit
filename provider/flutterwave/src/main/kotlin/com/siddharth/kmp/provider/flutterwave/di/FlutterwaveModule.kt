package com.siddharth.kmp.provider.flutterwave.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.flutterwave.FlutterwaveGateway
import com.siddharth.kmp.provider.hostedwebview.HostedCheckoutRelay
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module for `provider:flutterwave`.
 *
 * [HostedCheckoutRelay] is already a Koin single contributed by `hostedWebViewModule` — this module
 * resolves the same instance via `get()` rather than creating a second relay, so Flutterwave's
 * checkout shares the one `HostedCheckoutHost` composable mounted by the consuming app with every
 * other hosted-checkout gateway.
 */
val flutterwaveModule: Module =
    module {
        single { FlutterwaveGateway(get()) } bind PaymentGateway::class
    }
