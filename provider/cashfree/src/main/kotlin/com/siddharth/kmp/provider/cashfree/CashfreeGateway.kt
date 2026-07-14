package com.siddharth.kmp.provider.cashfree

import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.core.api.CFSession
import com.cashfree.pg.core.api.exception.CFException
import com.cashfree.pg.ui.api.CFDropCheckoutPayment
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
import kotlin.coroutines.resume

/**
 * Cashfree provider built on the nextgen SDK (`com.cashfree.pg.*`, [CFPaymentGatewayService]). Aimed
 * at the India stack: UPI (incl. the sandbox UPI simulator), cards, and net banking.
 *
 * Callback-era reality: the SDK reports terminal state through a `CFCheckoutResponseCallback` set on
 * the host Activity in `onCreate` — not a return value. [CashfreeCheckoutRelay] bridges that callback
 * into the `suspendCancellableCoroutine` this gateway suspends on. See the consumer app's wiring for
 * how the Activity forwards the SDK callback.
 *
 * Client-result-is-a-hint: `onPaymentVerify` means the SDK *initiated* verification — the
 * orchestrator still confirms server-side before trusting a [PaymentResult.Success]. Nothing secret
 * is displayed; [Redactor] gates every rendered payload.
 */
class CashfreeGateway(
    private val relay: CashfreeCheckoutRelay,
) : PaymentGateway {
    override val id: GatewayId = GatewayId("cashfree")

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = "Cashfree",
            status = GatewayStatus.SANDBOX_READY,
            capabilities =
                setOf(
                    Capability.ONE_TIME_PAYMENT,
                    Capability.UPI,
                    Capability.CARDS,
                    Capability.NET_BANKING,
                ),
            region = "India",
            docsPath = "docs/providers/cashfree.md",
            blurb =
                "UPI / cards / net-banking checkout via the Cashfree nextgen SDK. The sandbox UPI " +
                    "simulator lets you approve or decline a UPI collect request end-to-end without a real " +
                    "PSP app.",
        )

    /**
     * Cashfree's session material is the **`payment_session_id`** plus the **`order_id`**, both minted
     * server-side in `POST /orders` and delivered in [CreatedOrder.providerParams]. The client never
     * creates them. Missing either is a hard preparation failure.
     */
    override suspend fun prepare(created: CreatedOrder): PreparedPayment {
        val params = created.providerParams
        val paymentSessionId =
            params[KEY_PAYMENT_SESSION_ID]
                ?: throw PaymentPreparationException("Cashfree order missing '$KEY_PAYMENT_SESSION_ID'")
        val orderId = params[KEY_ORDER_ID] ?: created.order.orderId

        return PreparedPayment(
            gatewayId = id,
            orderId = orderId,
            amount = created.order.amount,
            params =
                mapOf(
                    KEY_PAYMENT_SESSION_ID to paymentSessionId,
                    KEY_ORDER_ID to orderId,
                ),
        )
    }

    override suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult {
        val androidHost =
            host as? AndroidPaymentHost
                ?: return configMissing("Cashfree requires an AndroidPaymentHost")

        val paymentSessionId =
            prepared.params[KEY_PAYMENT_SESSION_ID]
                ?: return configMissing("Cashfree payment missing '$KEY_PAYMENT_SESSION_ID'")
        val orderId = prepared.params[KEY_ORDER_ID] ?: prepared.orderId

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                relay.clearPending()
                AppLog.w("Cashfree payment coroutine cancelled for order=$orderId", tag = TAG)
            }

            try {
                // Register the one-shot relay listener BEFORE doPayment so we can't miss a fast
                // callback. suspendCancellableCoroutine + isActive guard => resume exactly once.
                relay.awaitResult { outcome ->
                    if (continuation.isActive) {
                        continuation.resume(outcome.toPaymentResult(orderId))
                    }
                }

                val cfSession =
                    CFSession
                        .CFSessionBuilder()
                        .setEnvironment(CFSession.Environment.SANDBOX)
                        .setPaymentSessionID(paymentSessionId)
                        .setOrderId(orderId)
                        .build()

                // CFDropCheckoutPayment renders Cashfree's full drop-in (UPI + cards + net-banking).
                val dropPayment =
                    CFDropCheckoutPayment
                        .CFDropCheckoutPaymentBuilder()
                        .setSession(cfSession)
                        .build()

                AppLog.i("Launching Cashfree drop checkout for order=$orderId", tag = TAG)
                CFPaymentGatewayService.getInstance().doPayment(androidHost.activity, dropPayment)
            } catch (e: CFException) {
                // Thrown synchronously by the builders / doPayment on a config problem.
                relay.clearPending()
                if (continuation.isActive) {
                    continuation.resume(sdkError(e.message ?: "Cashfree SDK error", e))
                }
            } catch (e: IllegalStateException) {
                // Relay already had a payment in flight.
                if (continuation.isActive) {
                    continuation.resume(configMissing(e.message ?: "Cashfree launcher not ready"))
                }
            }
        }
    }

    /** Map the relay [CashfreeCheckoutRelay.Outcome] into the normalized [PaymentResult]. */
    private fun CashfreeCheckoutRelay.Outcome.toPaymentResult(orderId: String): PaymentResult =
        when (this) {
            is CashfreeCheckoutRelay.Outcome.Verify -> {
                AppLog.i("Cashfree onPaymentVerify order=${this.orderId}", tag = TAG)
                PaymentResult.Success(
                    // paymentId is the order id — Cashfree's client callback returns only the orderId;
                    // the actual cf_payment_id is resolved server-side during verification.
                    paymentId = this.orderId,
                    // Unredacted, server-bound only — forwarded to PaymentBackend.verify.
                    verification =
                        mapOf(
                            "order_id" to this.orderId,
                            "cf_status" to "verify",
                        ),
                    raw =
                        Redactor.redact(
                            label = "cashfree.checkout.verify",
                            raw =
                                mapOf(
                                    "status" to "verify",
                                    "order_id" to this.orderId,
                                ),
                        ),
                )
            }

            is CashfreeCheckoutRelay.Outcome.Failure -> {
                AppLog.w("Cashfree onPaymentFailure order=${this.orderId}: $errorMessage", tag = TAG)
                PaymentResult.Failure(
                    code = mapFailureCode(errorCode, errorMessage),
                    message = UiText.of(errorMessage),
                    raw =
                        Redactor.redact(
                            label = "cashfree.checkout.failure",
                            raw =
                                mapOf(
                                    "status" to "failure",
                                    "order_id" to this.orderId,
                                    "error" to errorMessage,
                                    "error_code" to errorCode.orEmpty(),
                                ),
                        ),
                )
            }
        }

    /**
     * Map Cashfree's `CFErrorResponse` into the normalized taxonomy. A user cancel surfaces via
     * `onPaymentFailure` with a cancellation-shaped code/message (the SDK has no separate cancel
     * callback), so we detect it here and normalize to [PaymentResult.Cancelled]'s failure sibling.
     */
    private fun mapFailureCode(
        errorCode: String?,
        errorMessage: String,
    ): FailureCode {
        val haystack = "${errorCode.orEmpty()} $errorMessage".lowercase()
        return when {
            "cancel" in haystack -> FailureCode.USER_CANCELLED
            "network" in haystack || "timeout" in haystack -> FailureCode.NETWORK_ERROR
            "decline" in haystack || "failed" in haystack || "insufficient" in haystack ->
                FailureCode.GATEWAY_DECLINED
            else -> FailureCode.SDK_ERROR
        }
    }

    private fun configMissing(message: String): PaymentResult.Failure {
        AppLog.e("Cashfree config error: $message", tag = TAG)
        return PaymentResult.Failure(
            code = FailureCode.CONFIG_MISSING,
            message = UiText.of(message),
            raw = Redactor.redact("cashfree.config.error", mapOf("error" to message)),
        )
    }

    private fun sdkError(
        message: String,
        cause: Throwable?,
    ): PaymentResult.Failure {
        AppLog.e("Cashfree SDK error: $message", cause, tag = TAG)
        return PaymentResult.Failure(
            code = FailureCode.SDK_ERROR,
            message = UiText.of(message),
            raw = Redactor.redact("cashfree.sdk.error", mapOf("error" to message)),
        )
    }

    private companion object {
        const val TAG = "CashfreeGateway"
        const val KEY_PAYMENT_SESSION_ID = "payment_session_id"
        const val KEY_ORDER_ID = "order_id"
    }
}
