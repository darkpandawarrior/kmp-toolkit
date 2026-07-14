package com.siddharth.kmp.provider.wallet.di

import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.paymentsapi.WalletLedgerPort
import com.siddharth.kmp.provider.wallet.HttpWalletLedgerPort
import com.siddharth.kmp.provider.wallet.WalletConfig
import com.siddharth.kmp.provider.wallet.WalletGateway
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * One [PaymentGateway] per [WalletConfig], sharing the app's existing `HttpClient`/`PaymentApiConfig`,
 * plus a single [WalletLedgerPort] the orchestrator uses for the split-payment compensating credit.
 */
fun walletModule(configs: List<WalletConfig>) =
    module {
        configs.forEach { config ->
            single(qualifier = named(config.gatewayId.value)) {
                WalletGateway(config, get(), get())
            } bind PaymentGateway::class
        }
        single<WalletLedgerPort> { HttpWalletLedgerPort(get(), get()) }
    }
