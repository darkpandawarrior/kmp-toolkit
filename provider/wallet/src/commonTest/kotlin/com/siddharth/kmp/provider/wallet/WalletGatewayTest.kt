package com.siddharth.kmp.provider.wallet

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.CreatedOrder
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.OrderRef
import com.siddharth.kmp.paymentsapi.PaymentApiConfig
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentPreparationException
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PreparedPayment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class WalletGatewayTest {
    private val config =
        WalletConfig(
            gatewayId = GatewayId("wallet"),
            displayName = "Wallet",
            walletAccountId = "wallet_user1",
            docsPath = "docs/providers/wallet.md",
            blurb = "test",
            capabilities = setOf(Capability.ONE_TIME_PAYMENT),
            status = GatewayStatus.SANDBOX_READY,
        )

    private val order =
        CreatedOrder(
            order = OrderRef("order_1", "coffee_149", Money.inr(100)),
            gatewayId = GatewayId("wallet"),
            providerParams = emptyMap(),
        )

    private fun clientReturning(balanceMinor: Long) =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            engine {
                addHandler { request ->
                    assertEquals("/wallet/wallet_user1/balance", request.url.encodedPath)
                    respond(
                        content = """{"accountId":"wallet_user1","balanceMinor":$balanceMinor}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }

    @Test
    fun `prepare fails on insufficient balance`() =
        runTest {
            val gateway = WalletGateway(config, clientReturning(balanceMinor = 50), PaymentApiConfig())

            assertFailsWith<PaymentPreparationException> {
                gateway.prepare(order)
            }
        }

    @Test
    fun `prepare succeeds when balance covers the order amount`() =
        runTest {
            val gateway = WalletGateway(config, clientReturning(balanceMinor = 100_000), PaymentApiConfig())

            val prepared = gateway.prepare(order)

            assertEquals("order_1", prepared.orderId)
        }

    @Test
    fun `pay debits and returns Success with the ledger txn ref`() =
        runTest {
            val client =
                HttpClient(MockEngine) {
                    install(ContentNegotiation) { json() }
                    engine {
                        addHandler { request ->
                            assertEquals("/wallet/wallet_user1/debit", request.url.encodedPath)
                            respond(
                                content =
                                    """{"txnId":"ledger_txn_abc","accountId":"wallet_user1","balanceMinor":9900}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    }
                }
            val gateway = WalletGateway(config, client, PaymentApiConfig())
            val prepared =
                PreparedPayment(
                    gatewayId = GatewayId("wallet"),
                    orderId = order.order.orderId,
                    amount = order.order.amount,
                    params = emptyMap(),
                )

            val result = gateway.pay(FakeHost, prepared)

            val success = assertIs<PaymentResult.Success>(result)
            assertEquals("ledger_txn_abc", success.paymentId)
        }

    private object FakeHost : PaymentHost
}
