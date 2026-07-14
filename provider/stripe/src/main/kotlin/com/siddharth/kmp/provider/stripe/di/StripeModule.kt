package com.siddharth.kmp.provider.stripe.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.stripe.StripeGateway
import com.siddharth.kmp.provider.stripe.StripePaymentLauncherHost
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module for the Stripe provider. The gateway is bound to [PaymentGateway] (via `bind`) so the
 * orchestration module's `getAll<PaymentGateway>()` collects it alongside every other provider — the
 * whole point of the registry pattern: adding this module is the only wiring an app does.
 *
 * [StripePaymentLauncherHost] is a singleton the app also reaches (to `attach` its Compose/Activity-
 * scoped `PaymentSheet`), so it must be shared between the gateway and the UI — hence `single`.
 */
val stripeModule: Module =
    module {
        single { StripePaymentLauncherHost() }
        single { StripeGateway(launcherHost = get()) } bind PaymentGateway::class
    }
