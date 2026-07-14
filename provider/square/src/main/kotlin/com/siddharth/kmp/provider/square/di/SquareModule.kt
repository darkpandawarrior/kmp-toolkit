package com.siddharth.kmp.provider.square.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.square.SquareGateway
import org.koin.dsl.module

val squareModule =
    module {
        single<PaymentGateway> { SquareGateway() }
    }
