package com.siddharth.kmp.provider.mpesa

import com.siddharth.kmp.common.AppLog
import com.siddharth.kmp.paymentsapi.CreatedOrder
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayMeta
import com.siddharth.kmp.paymentsapi.PaymentApiConfig
import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PendingReason
import com.siddharth.kmp.paymentsapi.PreparedPayment
import com.siddharth.kmp.paymentsapi.Redactor
import io.ktor.client.HttpClient
import io.ktor.client.request.post

/**
 * Archetype-D, same shape as [com.siddharth.kmp.provider.mobilemoney.MobileMoneyGateway]: `pay()`
 * kicks off the mock delayed-flip (`POST /mock/mpesa/{orderId}/settle`) and returns `Pending`
 * immediately — a poll-with-backoff on the consumer side resolves it once its backend flips it.
 */
class MpesaGateway(
    private val config: MpesaConfig,
    private val httpClient: HttpClient,
    private val apiConfig: PaymentApiConfig,
) : PaymentGateway {
    override val id: GatewayId = config.gatewayId

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = config.displayName,
            status = config.status,
            capabilities = config.capabilities,
            region = config.region,
            docsPath = config.docsPath,
            blurb = config.blurb,
        )

    override suspend fun prepare(created: CreatedOrder): PreparedPayment =
        PreparedPayment(
            gatewayId = id,
            orderId = created.order.orderId,
            amount = created.order.amount,
            params = created.providerParams,
        )

    override suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult {
        val baseUrl = apiConfig.baseUrl.trimEnd('/')
        runCatching {
            httpClient.post("$baseUrl/mock/mpesa/${prepared.orderId}/settle?delayMs=$MOCK_FLIP_DELAY_MS")
        }.onFailure { AppLog.w("Could not schedule mock mpesa flip for ${prepared.orderId}", it, tag = TAG) }

        return PaymentResult.Pending(
            reason = PendingReason.AWAITING_WEBHOOK,
            raw = Redactor.redact("${id.value}_pending", mapOf("order_id" to prepared.orderId, "mode" to "async")),
        )
    }

    private companion object {
        const val TAG = "MpesaGateway"
        const val MOCK_FLIP_DELAY_MS = 3_000L
    }
}
