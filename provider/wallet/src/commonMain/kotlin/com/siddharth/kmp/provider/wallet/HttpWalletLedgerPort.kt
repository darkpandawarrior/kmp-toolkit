package com.siddharth.kmp.provider.wallet

import com.siddharth.kmp.paymentsapi.InsufficientWalletBalanceException
import com.siddharth.kmp.paymentsapi.PaymentApiConfig
import com.siddharth.kmp.paymentsapi.WalletDebitRequest
import com.siddharth.kmp.paymentsapi.WalletLedgerPort
import com.siddharth.kmp.paymentsapi.WalletRefundRequest
import com.siddharth.kmp.paymentsapi.WalletTransactionResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/**
 * [WalletLedgerPort] over the same `/wallet/{accountId}/debit` + `/refund` routes [WalletGateway]
 * uses for its own debit — this is the orchestrator's seam for the split-payment compensating
 * credit, kept off [WalletGateway] itself (see that class's doc comment on why refund isn't there).
 */
class HttpWalletLedgerPort(
    private val httpClient: HttpClient,
    private val apiConfig: PaymentApiConfig,
) : WalletLedgerPort {
    private val base: String get() = apiConfig.baseUrl.trimEnd('/')

    override suspend fun debit(
        walletAccountId: String,
        idempotencyKey: String,
        amountMinor: Long,
    ): String =
        try {
            val response: WalletTransactionResponse =
                httpClient
                    .post("$base/wallet/$walletAccountId/debit") {
                        contentType(ContentType.Application.Json)
                        setBody(WalletDebitRequest(idempotencyKey, amountMinor))
                    }.body()
            response.txnId
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.BadRequest) {
                throw InsufficientWalletBalanceException(walletAccountId)
            }
            throw e
        }

    override suspend fun refund(
        walletAccountId: String,
        idempotencyKey: String,
        amountMinor: Long,
    ): String {
        val response: WalletTransactionResponse =
            httpClient
                .post("$base/wallet/$walletAccountId/refund") {
                    contentType(ContentType.Application.Json)
                    setBody(WalletRefundRequest(idempotencyKey, amountMinor))
                }.body()
        return response.txnId
    }
}
