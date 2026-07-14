package com.siddharth.kmp.provider.peach.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.peach.PeachGateway
import org.koin.dsl.bind
import org.koin.dsl.module

/** Single [PaymentGateway], same no-fan-out shape as [com.siddharth.kmp.provider.cash.di.cashModule]. */
val peachModule =
    module {
        single { PeachGateway(get()) } bind PaymentGateway::class
    }
