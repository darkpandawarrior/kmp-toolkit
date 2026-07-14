package com.siddharth.kmp.provider.hostedwebview.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.provider.hostedwebview.HostedCheckoutRelay
import com.siddharth.kmp.provider.hostedwebview.HostedGatewayConfig
import com.siddharth.kmp.provider.hostedwebview.HostedWebViewGateway
import org.koin.dsl.module

/**
 * One [HostedCheckoutRelay] shared by every hosted-webview gateway, plus one [PaymentGateway] per
 * [HostedGatewayConfig].
 */
fun hostedWebViewModule(configs: List<HostedGatewayConfig>) =
    module {
        single { HostedCheckoutRelay() }
        // Exposed so the app can mount one HostedCheckoutHost that knows every configured gateway.
        single { configs }
        configs.forEach { config ->
            single<PaymentGateway>(
                qualifier =
                    org.koin.core.qualifier
                        .named(config.gatewayId.value),
            ) {
                HostedWebViewGateway(config, get())
            }
        }
    }
