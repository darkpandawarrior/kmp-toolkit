package com.siddharth.kmp.provider.applepay.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.applepay.ApplePayGateway
import org.koin.dsl.module

// iosMain, not commonMain: ApplePayGateway only exists where PassKit does, so there is nothing to
// register on any other target.
val applePayModule =
    module {
        single<PaymentGateway> { ApplePayGateway() }
    }
