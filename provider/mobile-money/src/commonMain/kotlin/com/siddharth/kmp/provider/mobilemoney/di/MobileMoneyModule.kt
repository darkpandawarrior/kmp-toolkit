package com.siddharth.kmp.provider.mobilemoney.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.mobilemoney.MobileMoneyConfig
import com.siddharth.kmp.provider.mobilemoney.MobileMoneyGateway
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** One [PaymentGateway] per [MobileMoneyConfig], sharing the app's existing `HttpClient`/`PaymentApiConfig`. */
fun mobileMoneyModule(configs: List<MobileMoneyConfig>) =
    module {
        configs.forEach { config ->
            single<PaymentGateway>(qualifier = named(config.gatewayId.value)) {
                MobileMoneyGateway(config, get(), get())
            }
        }
    }
