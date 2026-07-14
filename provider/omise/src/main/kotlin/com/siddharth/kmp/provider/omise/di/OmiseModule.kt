package com.siddharth.kmp.provider.omise.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.omise.OmiseGateway
import org.koin.dsl.module

val omiseModule =
    module {
        single<PaymentGateway> { OmiseGateway() }
    }
