package com.siddharth.kmp.provider.cashfree.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.cashfree.CashfreeCheckoutRelay
import com.siddharth.kmp.provider.cashfree.CashfreeGateway
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module for the Cashfree provider. The gateway is bound to [PaymentGateway] (via `bind`) so the
 * orchestration module's `getAll<PaymentGateway>()` collects it alongside every other provider —
 * adding this module is the only wiring an app does.
 *
 * [CashfreeCheckoutRelay] is a singleton the app also reaches (its Activity `onCreate` sets a
 * `CFCheckoutResponseCallback` that forwards into this relay), so it must be shared between the
 * gateway and the Activity — hence `single`.
 */
val cashfreeModule: Module =
    module {
        single { CashfreeCheckoutRelay() }
        single { CashfreeGateway(relay = get()) } bind PaymentGateway::class
    }
