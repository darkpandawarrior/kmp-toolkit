package com.siddharth.kmp.provider.cash.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.cash.CashGateway
import org.koin.dsl.bind
import org.koin.dsl.module

/** Single [PaymentGateway] — cash has no per-region fan-out, unlike mobile-money/hosted-webview. */
val cashModule =
    module {
        single { CashGateway() } bind PaymentGateway::class
    }
