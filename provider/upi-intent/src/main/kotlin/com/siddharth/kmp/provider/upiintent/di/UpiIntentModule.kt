package com.siddharth.kmp.provider.upiintent.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.upiintent.UpiIntentGateway
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module for the raw-UPI-intent provider.
 *
 * The gateway is registered as its concrete [UpiIntentGateway] but *also bound* to the
 * [PaymentGateway] supertype via `bind`. That is what lets the orchestrator collect every provider
 * with `getAll<PaymentGateway>()` — each provider contributes one distinct concrete `single`, so
 * there is no definition override, and all of them show up in the multi-binding.
 */
val upiIntentModule: Module =
    module {
        single { UpiIntentGateway() } bind PaymentGateway::class
    }
