package com.siddharth.kmp.provider.razorpay

import com.razorpay.Checkout
import com.siddharth.kmp.common.AppLog
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
import com.siddharth.kmp.paymentsapi.PaymentPreparationException
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PreparedPayment
import com.siddharth.kmp.paymentsapi.Redactor
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Razorpay Standard Checkout gateway.
 *
 * ── The Activity-callback → coroutine bridge (the interesting part) ─────────────────────────────
 * `Checkout.open(activity, options)` is fire-and-forget: the SDK reports its result by invoking
 * `com.razorpay.PaymentResultWithDataListener` **on the Activity**, not through a per-call callback.
 * Our [PaymentGateway] contract is a single suspending [pay] and deliberately keeps Activity
 * references out of providers. We reconcile the two like this:
 *
 *   1. The app's `MainActivity` implements Razorpay's `PaymentResultWithDataListener` and, from
 *      `onPaymentSuccess` / `onPaymentError`, flattens `PaymentData` and forwards the result to
 *      [RazorpayCallbackRelay].
 *   2. Here in [pay] we [RazorpayCallbackRelay.register] a one-shot listener, suspend on
 *      [suspendCancellableCoroutine], then `Checkout.open(host.activity, options)`.
 *   3. When the relay fires, we resume the coroutine exactly once and clear the slot.
 *
 * ── Verification vs. redaction ──────────────────────────────────────────────────────────────────
 * `razorpay_signature` is the HMAC the *server* uses to prove the payment is authentic. It goes into
 * [PaymentResult.Success.verification] UNREDACTED (server-bound, never displayed) and ALSO into
 * [PaymentResult.Success.raw] via [Redactor], where it appears masked. Signature verification is a
 * server responsibility — the client never verifies it and a client Success is only a hint.
 *
 * No key or secret is ever hardcoded: `key_id` arrives in [CreatedOrder.providerParams] from the
 * backend (sandbox keys are `rzp_test_…`); the secret lives only on the server.
 */
class RazorpayGateway(
    private val relay: RazorpayCallbackRelay,
) : PaymentGateway {
    override val id: GatewayId = GatewayId("razorpay")

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = "Razorpay",
            status = GatewayStatus.SANDBOX_READY,
            capabilities =
                setOf(
                    Capability.ONE_TIME_PAYMENT,
                    Capability.UPI,
                    Capability.CARDS,
                    Capability.NET_BANKING,
                    Capability.WALLET,
                ),
            region = "India",
            docsPath = "docs/providers/razorpay.md",
            blurb =
                "Razorpay Standard Checkout via the official SDK. Client success is confirmed " +
                    "server-side by verifying razorpay_signature before the order is trusted.",
        )

    /**
     * Repackage the backend's provider session material (`key_id`, `order_id`, `amount`, `currency`)
     * into a [PreparedPayment]. No network hop here — the orchestrator already created the order.
     */
    override suspend fun prepare(created: CreatedOrder): PreparedPayment {
        val p = created.providerParams
        val keyId =
            p["key_id"]
                ?: throw PaymentPreparationException("Razorpay order missing key_id")
        val razorpayOrderId =
            p["order_id"]
                ?: throw PaymentPreparationException("Razorpay order missing order_id")

        val params =
            mapOf(
                "key_id" to keyId,
                "order_id" to razorpayOrderId,
                "amount" to
                    created.order.amount.amountMinor
                        .toString(),
                "currency" to created.order.amount.currency,
            )
        AppLog.d("prepared Razorpay order=${created.order.orderId} rzpOrder=$razorpayOrderId", tag = TAG)
        return PreparedPayment(
            gatewayId = id,
            orderId = created.order.orderId,
            amount = created.order.amount,
            params = params,
        )
    }

    override suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult {
        val androidHost =
            host as? AndroidPaymentHost
                ?: return failure(FailureCode.SDK_ERROR, "Razorpay requires an Android host", "host_not_android")

        val keyId =
            prepared.params["key_id"]
                ?: return failure(FailureCode.CONFIG_MISSING, "Missing Razorpay key_id", "no_key_id")
        val orderId =
            prepared.params["order_id"]
                ?: return failure(FailureCode.CONFIG_MISSING, "Missing Razorpay order_id", "no_order_id")

        return suspendCancellableCoroutine { cont ->
            // resume-once guard: the relay is single-slot, but recreation / defensive double-emit
            // could invoke the listener more than once. Only the first result wins.
            val resumed = AtomicBoolean(false)

            fun finishOnce(result: PaymentResult) {
                relay.clear()
                if (resumed.compareAndSet(false, true) && cont.isActive) {
                    cont.resume(result)
                }
            }

            relay.register { callback -> finishOnce(mapCallback(callback)) }

            // If the coroutine is cancelled (screen gone) while checkout is open, release the slot.
            cont.invokeOnCancellation { relay.clear() }

            val options = buildOptions(keyId, orderId, prepared)

            try {
                val checkout = Checkout.getInstance(androidHost.activity)
                checkout.setKeyID(keyId)
                checkout.open(androidHost.activity, options)
            } catch (e: Exception) {
                AppLog.e("Checkout.open failed", e, tag = TAG)
                finishOnce(
                    failure(FailureCode.SDK_ERROR, "Could not open Razorpay checkout", e.message ?: "open_failed"),
                )
            }
        }
    }

    /** Build the Razorpay Standard Checkout options JSON. Amount is in minor units (paise). */
    private fun buildOptions(
        keyId: String,
        orderId: String,
        prepared: PreparedPayment,
    ): JSONObject =
        JSONObject().apply {
            put("key", keyId)
            put("order_id", orderId)
            put("amount", prepared.amount.amountMinor) // paise
            put("currency", prepared.amount.currency)
            // ponytail: generic display name — R17, no reference-app branding in the public toolkit.
            put("name", "kmp-toolkit Demo")
            put("description", "Order ${prepared.orderId}")
        }

    /** Map a relayed Razorpay callback into the normalized [PaymentResult]. */
    internal fun mapCallback(callback: RazorpayCallbackResult): PaymentResult =
        when (callback) {
            is RazorpayCallbackResult.Success -> {
                val paymentId = callback.razorpayPaymentId.orEmpty()
                // verification: UNREDACTED, server-bound. razorpay_signature is the HMAC the server
                // recomputes to authenticate the payment — it must reach the backend intact.
                val verification =
                    buildMap {
                        put("razorpay_order_id", callback.razorpayOrderId.orEmpty())
                        put("razorpay_payment_id", paymentId)
                        put("razorpay_signature", callback.razorpaySignature.orEmpty())
                    }
                // raw: the same fields (plus any extra) run through the Redactor — signature appears
                // MASKED here, safe to render in the Lab timeline.
                val raw =
                    Redactor.redact(
                        RAW_LABEL,
                        buildMap<String, String?> {
                            put("razorpay_order_id", callback.razorpayOrderId)
                            put("razorpay_payment_id", paymentId)
                            put("razorpay_signature", callback.razorpaySignature)
                            putAll(callback.extra)
                        },
                    )
                AppLog.i(
                    "Razorpay success paymentId=$paymentId (unverified client claim; server verifies signature)",
                    tag = TAG,
                )
                PaymentResult.Success(paymentId = paymentId, verification = verification, raw = raw)
            }

            is RazorpayCallbackResult.Error -> {
                val code = mapErrorCode(callback.code)
                val raw =
                    Redactor.redact(
                        RAW_LABEL,
                        buildMap<String, String?> {
                            put("code", callback.code.toString())
                            put("description", callback.description)
                            putAll(callback.extra)
                        },
                    )
                AppLog.w("Razorpay error code=${callback.code} desc=${callback.description}", tag = TAG)
                PaymentResult.Failure(
                    code = code,
                    message = UiText.of(callback.description ?: "Razorpay payment failed"),
                    raw = raw,
                )
            }
        }

    /**
     * Razorpay error code → normalized [FailureCode].
     *  - PAYMENT_CANCELED → USER_CANCELLED (user dismissed the sheet)
     *  - NETWORK_ERROR / TLS_ERROR → NETWORK_ERROR
     *  - INVALID_OPTIONS → CONFIG_MISSING (bad key/order/amount we sent)
     *  - anything else → GATEWAY_DECLINED (the bank/gateway rejected the payment)
     */
    private fun mapErrorCode(rzpCode: Int): FailureCode =
        when (rzpCode) {
            Checkout.PAYMENT_CANCELED -> FailureCode.USER_CANCELLED
            Checkout.NETWORK_ERROR, Checkout.TLS_ERROR -> FailureCode.NETWORK_ERROR
            Checkout.INVALID_OPTIONS -> FailureCode.CONFIG_MISSING
            else -> FailureCode.GATEWAY_DECLINED
        }

    private fun failure(
        code: FailureCode,
        message: String,
        rawReason: String,
    ): PaymentResult.Failure =
        PaymentResult.Failure(
            code = code,
            message = UiText.of(message),
            raw = Redactor.redact(RAW_LABEL, mapOf("error" to rawReason)),
        )

    private companion object {
        const val TAG = "RazorpayGateway"
        const val RAW_LABEL = "razorpay_result"
    }
}
