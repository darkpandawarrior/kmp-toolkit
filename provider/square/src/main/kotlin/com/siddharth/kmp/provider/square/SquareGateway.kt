package com.siddharth.kmp.provider.square

import com.siddharth.kmp.common.UiText
import com.siddharth.kmp.paymentsapi.AndroidPaymentHost
import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.CreatedOrder
import com.siddharth.kmp.paymentsapi.FailureCode
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayMeta
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PreparedPayment
import com.siddharth.kmp.paymentsapi.Redactor
import com.siddharth.kmp.paymentsapi.SimulatedPayment
import kotlinx.coroutines.suspendCancellableCoroutine
import sqip.CardEntry
import sqip.InAppPaymentsSdk
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Square — Tier-1, real In-App Payments Card Entry SDK (`sqip.CardEntry`, `sqip.InAppPaymentsSdk`,
 * hosted at `sdk.squareup.com/public/android`, not Maven Central).
 *
 * The backend decides real vs mock via a configured Square sandbox Application ID (same auto-degrade
 * pattern as the other gateways) and hands the (non-secret, client-embeddable) Application ID down
 * through [CreatedOrder.providerParams] — the access token that actually charges the resulting nonce
 * never leaves the backend.
 *
 * **Real** (Application ID present): launches Square's real `CardEntryActivity` for card
 * tokenization. Square's SDK predates `ActivityResultContract` — `CardEntry.startCardEntryActivity`
 * calls `Activity.startActivityForResult` directly, so the result arrives via
 * `MainActivity.onActivityResult` → `CardEntry.handleActivityResult` → [SquareCallbackRelay], not
 * `AndroidPaymentHost.registerForResult` (mirrors the Razorpay bridge, for the same legacy-API
 * reason). The nonce becomes `PaymentResult.Success.paymentId`; the backend's `verify()` charges it
 * server-side (`POST /v2/payments`).
 *
 * **Mock** (Application ID absent): [SimulatedPayment] — no live sandbox credentials configured, so
 * this ships `MOCK_MODE` by default.
 *
 * `docs: https://developer.squareup.com/docs/in-app-payments-sdk/what-it-does`
 */
class SquareGateway : PaymentGateway {
    override val id: GatewayId = GatewayId("square")

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = "Square",
            status = GatewayStatus.MOCK_MODE,
            capabilities = setOf(Capability.ONE_TIME_PAYMENT, Capability.CARDS),
            region = "Global",
            docsPath = "docs/providers/square.md",
            blurb =
                "Square In-App Payments Card Entry SDK — real card tokenization when a sandbox " +
                    "Application ID is configured backend-side, mock fallback otherwise.",
        )

    override suspend fun prepare(created: CreatedOrder): PreparedPayment {
        val applicationId = created.providerParams[KEY_APPLICATION_ID]
        return PreparedPayment(
            gatewayId = id,
            orderId = created.order.orderId,
            amount = created.order.amount,
            params = if (applicationId.isNullOrBlank()) emptyMap() else mapOf(KEY_APPLICATION_ID to applicationId),
        )
    }

    override suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult {
        val applicationId = prepared.params[KEY_APPLICATION_ID]
        if (applicationId.isNullOrBlank()) return SimulatedPayment.run(id, prepared)

        val androidHost =
            host as? AndroidPaymentHost
                ?: return failure(FailureCode.SDK_ERROR, "Square requires an Android host")

        InAppPaymentsSdk.squareApplicationId = applicationId

        return suspendCancellableCoroutine { cont ->
            val resumed = AtomicBoolean(false)

            fun finishOnce(result: PaymentResult) {
                if (resumed.compareAndSet(false, true) && cont.isActive) cont.resume(result) { _, _, _ -> }
                SquareCallbackRelay.clear()
            }

            SquareCallbackRelay.register { result ->
                when (result) {
                    is SquareCallbackResult.Success ->
                        finishOnce(
                            PaymentResult.Success(
                                paymentId = result.nonce,
                                verification = mapOf("card_last_four" to (result.cardLastFour ?: "")),
                                raw = redact("success", mapOf("card_last_four" to (result.cardLastFour ?: ""))),
                            ),
                        )
                    SquareCallbackResult.Canceled ->
                        finishOnce(PaymentResult.Cancelled(raw = redact("cancelled", emptyMap())))
                }
            }

            CardEntry.startCardEntryActivity(androidHost.activity)
        }
    }

    private fun failure(
        code: FailureCode,
        message: String,
    ): PaymentResult.Failure =
        PaymentResult.Failure(
            code = code,
            message = UiText.of(message),
            raw = redact("failure", mapOf("error" to message)),
        )

    private fun redact(
        label: String,
        extra: Map<String, String>,
    ) = Redactor.redact("square_$label", extra)

    private companion object {
        const val KEY_APPLICATION_ID = "application_id"
    }
}
