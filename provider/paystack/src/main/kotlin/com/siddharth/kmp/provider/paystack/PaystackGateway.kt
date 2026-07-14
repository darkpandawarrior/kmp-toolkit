package com.siddharth.kmp.provider.paystack

import com.siddharth.kmp.common.AppLog
import com.siddharth.kmp.common.UiText
import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.CreatedOrder
import com.siddharth.kmp.paymentsapi.FailureCode
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayMeta
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentPreparationException
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PreparedPayment
import com.siddharth.kmp.paymentsapi.Redactor
import com.siddharth.kmp.provider.hostedwebview.HostedCheckoutRelay
import com.siddharth.kmp.provider.hostedwebview.HostedReturnOutcome
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Paystack: a redirect-cluster gateway built on `:provider:hosted-webview`'s shared checkout relay
 * rather than a native SDK. The checkout page is still a hosted Paystack URL, so `pay()` reuses the
 * shared [HostedCheckoutRelay]/`HostedCheckoutHost` mounted by the consuming app. What this module
 * owns natively is the gateway identity, [GatewayMeta], and return-URL contract.
 *
 * `status = MOCK_MODE` until a real backend secret is configured — same honesty rule as every other
 * MOCK_MODE gateway in this catalog.
 */
class PaystackGateway(
    private val relay: HostedCheckoutRelay,
) : PaymentGateway {
    override val id: GatewayId = GatewayId("paystack")

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = "Paystack",
            status = GatewayStatus.MOCK_MODE,
            capabilities = setOf(Capability.ONE_TIME_PAYMENT, Capability.CARDS),
            region = "Africa",
            docsPath = "docs/providers/paystack.md",
            blurb =
                "Still a hosted checkout page under the hood — the backend resolves the real " +
                    "`checkout_url` (genuine Paystack authorization_url, or the mock fallback); this " +
                    "module owns the return-URL contract and gateway identity natively.",
        )

    /** No network hop — the backend already builds `checkout_url` into `providerParams`. */
    override suspend fun prepare(created: CreatedOrder): PreparedPayment {
        val checkoutUrl =
            created.providerParams["checkout_url"]
                ?: throw PaymentPreparationException("Paystack order missing checkout_url")
        AppLog.d("prepared Paystack order=${created.order.orderId}", tag = TAG)
        return PreparedPayment(
            gatewayId = id,
            orderId = created.order.orderId,
            amount = created.order.amount,
            params = mapOf("checkout_url" to checkoutUrl),
        )
    }

    override suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult {
        val checkoutUrl =
            prepared.params["checkout_url"]
                ?: return failure(FailureCode.CONFIG_MISSING, "Missing Paystack checkout_url", "no_checkout_url")

        return suspendCancellableCoroutine { cont ->
            relay.register(id) { outcome -> if (cont.isActive) cont.resume(mapOutcome(outcome)) { _, _, _ -> } }
            cont.invokeOnCancellation { relay.clear(id) }
            relay.launch(id, checkoutUrl)
        }
    }

    internal fun mapOutcome(outcome: HostedReturnOutcome): PaymentResult =
        when (outcome) {
            is HostedReturnOutcome.Success ->
                PaymentResult.Success(
                    paymentId = outcome.paymentId ?: "unknown",
                    verification = outcome.paymentId?.let { mapOf("payment_id" to it) } ?: emptyMap(),
                    raw = redact("success", outcome.paymentId?.let { mapOf("payment_id" to it) } ?: emptyMap()),
                )
            is HostedReturnOutcome.Failure ->
                PaymentResult.Failure(
                    code = FailureCode.GATEWAY_DECLINED,
                    message = UiText.of(outcome.reason ?: "Paystack checkout reported a failure"),
                    raw = redact("failure", mapOf("reason" to (outcome.reason ?: ""))),
                )
            HostedReturnOutcome.Cancelled ->
                PaymentResult.Cancelled(raw = redact("cancelled", emptyMap()))
        }

    private fun failure(
        code: FailureCode,
        message: String,
        rawReason: String,
    ): PaymentResult.Failure =
        PaymentResult.Failure(
            code = code,
            message = UiText.of(message),
            raw = redact("failure", mapOf("error" to rawReason)),
        )

    private fun redact(
        outcome: String,
        extra: Map<String, String>,
    ) = Redactor.redact("paystack_$outcome", extra)

    private companion object {
        const val TAG = "PaystackGateway"
    }
}
