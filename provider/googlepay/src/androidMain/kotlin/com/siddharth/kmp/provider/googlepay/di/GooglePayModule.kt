package com.siddharth.kmp.provider.googlepay.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.googlepay.GooglePayGateway
import org.koin.dsl.module

val googlePayModule =
    module {
        single<PaymentGateway> { GooglePayGateway() }
    }
