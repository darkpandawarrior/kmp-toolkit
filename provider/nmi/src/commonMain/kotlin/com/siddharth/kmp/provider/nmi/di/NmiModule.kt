package com.siddharth.kmp.provider.nmi.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.nmi.NmiGateway
import org.koin.dsl.bind
import org.koin.dsl.module

/** Single [PaymentGateway], same no-fan-out shape as [com.siddharth.kmp.provider.cash.di.cashModule]. */
val nmiModule =
    module {
        single { NmiGateway(get()) } bind PaymentGateway::class
    }
