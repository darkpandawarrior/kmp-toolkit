package com.siddharth.kmp.provider.flutterwave

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
 * Flutterwave: a redirect-cluster gateway built on `:provider:hosted-webview`'s shared checkout
 * relay rather than a native SDK — the widest capability-per-gateway combo in the cluster (cards +
 * wallets + mobile-money + 3DS in one integration). `pay()` reuses the shared
 * [HostedCheckoutRelay]/`HostedCheckoutHost` mounted by the consuming app — nothing about the
 * checkout mechanics changes. What this module owns natively is the gateway identity, [GatewayMeta],
 * and return-URL contract.
 *
 * Flutterwave's Standard/Rave checkout genuinely spans cards, bank transfers, USSD, and
 * mobile-money (M-Pesa, MTN MoMo, Airtel Money, etc.) across Africa, with 3D Secure on the card
 * path — that combined depth is the whole point of giving it a dedicated module instead of a
 * config line. [Capability] has no dedicated `MOBILE_MONEY`/`THREE_DS` values yet, so those are
 * called out honestly in the blurb rather than invented as new enum cases here; the flags below
 * only claim what the existing enum can represent (cards + wallet as the closest fit for MoMo).
 *
 * `status = MOCK_MODE` until a real backend secret is configured — same honesty rule as every other
 * MOCK_MODE gateway in this catalog.
 */
class FlutterwaveGateway(
    private val relay: HostedCheckoutRelay,
) : PaymentGateway {
    override val id: GatewayId = GatewayId("flutterwave")

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = "Flutterwave",
            status = GatewayStatus.MOCK_MODE,
            capabilities = setOf(Capability.ONE_TIME_PAYMENT, Capability.CARDS, Capability.WALLET),
            region = "Africa",
            docsPath = "docs/providers/flutterwave.md",
            blurb =
                "Rave/Standard checkout spans cards (with 3D Secure), bank transfers, USSD and " +
                    "mobile-money (M-Pesa, MTN MoMo, Airtel Money) in one integration; still a hosted " +
                    "checkout page under the hood — the backend resolves the real `checkout_url` (genuine " +
                    "Flutterwave payment link, or the mock fallback); this module owns the return-URL " +
                    "contract and gateway identity natively.",
        )

    /** No network hop — the backend already builds `checkout_url` into `providerParams`. */
    override suspend fun prepare(created: CreatedOrder): PreparedPayment {
        val checkoutUrl =
            created.providerParams["checkout_url"]
                ?: throw PaymentPreparationException("Flutterwave order missing checkout_url")
        AppLog.d("prepared Flutterwave order=${created.order.orderId}", tag = TAG)
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
                ?: return failure(FailureCode.CONFIG_MISSING, "Missing Flutterwave checkout_url", "no_checkout_url")

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
                    message = UiText.of(outcome.reason ?: "Flutterwave checkout reported a failure"),
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
    ) = Redactor.redact("flutterwave_$outcome", extra)

    private companion object {
        const val TAG = "FlutterwaveGateway"
    }
}
