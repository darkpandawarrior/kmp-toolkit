package com.siddharth.kmp.provider.xendit.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.xendit.XenditConfig
import com.siddharth.kmp.provider.xendit.XenditGateway
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** One [PaymentGateway] for Xendit, sharing the app's existing `HttpClient`/`PaymentApiConfig`. */
fun xenditModule(config: XenditConfig = XenditConfig()) =
    module {
        single<PaymentGateway>(qualifier = named(config.gatewayId.value)) {
            XenditGateway(config, get(), get())
        }
    }
