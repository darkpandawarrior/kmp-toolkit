package com.siddharth.kmp.provider.googlepay

import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
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
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PreparedPayment
import com.siddharth.kmp.paymentsapi.Redactor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google Pay — a wallet *method* riding a payment gateway underneath (here, a "example"/TEST
 * tokenization gateway; production would set `gateway = "stripe"` + the real Stripe account's
 * `gatewayMerchantId`, since Google Pay hands back an encrypted token for a processor to charge, it
 * doesn't settle payments itself). `ENVIRONMENT_TEST` needs no live merchant account, so this ships
 * `SANDBOX_READY` — genuinely runnable by anyone with a Google account and a test card on file.
 *
 * `docs: https://developers.google.com/pay/api/android/guides/tutorial`
 *
 * Request-JSON shape adapted from `khalid64927/google-apple-pay` (Apache-2.0) — see
 * [GooglePayRequestBuilder] doc for why that repo can't be depended on directly. The
 * Activity-callback → coroutine bridge below is this module's own pattern (mirrors
 * [com.siddharth.kmp.paymentsapi.AndroidPaymentHost] usage in the Razorpay/Stripe providers), not
 * ported from that repo's Fragment-based resolver.
 */
class GooglePayGateway(
    private val config: GooglePayConfig = GooglePayConfig(),
) : PaymentGateway {
    private val requestBuilder = GooglePayRequestBuilder(config)

    override val id: GatewayId = GatewayId("googlepay")

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = "Google Pay",
            status = GatewayStatus.SANDBOX_READY,
            capabilities = setOf(Capability.ONE_TIME_PAYMENT, Capability.WALLET, Capability.CARDS),
            region = "Global",
            docsPath = "docs/providers/googlepay.md",
            blurb =
                "Google Pay via the real Play Services Wallet API, TEST environment — a genuine " +
                    "wallet method (not a settlement gateway) tokenizing to a processor underneath.",
        )

    override suspend fun prepare(created: CreatedOrder): PreparedPayment =
        PreparedPayment(
            gatewayId = id,
            orderId = created.order.orderId,
            amount = created.order.amount,
            params = mapOf("currency" to created.order.amount.currency),
        )

    override suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult {
        val androidHost =
            host as? AndroidPaymentHost
                ?: return failure(FailureCode.SDK_ERROR, "Google Pay requires an Android host")

        val paymentsClient: PaymentsClient =
            Wallet.getPaymentsClient(
                androidHost.activity,
                Wallet.WalletOptions
                    .Builder()
                    .setEnvironment(config.paymentsEnvironment)
                    .build(),
            )
        val requestJson = requestBuilder.paymentDataRequest(prepared.amount.amountMinor)
        val request = PaymentDataRequest.fromJson(requestJson.toString())

        return suspendCancellableCoroutine { cont ->
            val resumed = AtomicBoolean(false)

            fun finishOnce(result: PaymentResult) {
                if (resumed.compareAndSet(false, true) && cont.isActive) cont.resume(result) { _, _, _ -> }
            }

            val launcher =
                androidHost.registerForResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
                    if (activityResult.resultCode == Activity.RESULT_OK) {
                        val paymentData = activityResult.data?.let(PaymentData::getFromIntent)
                        finishOnce(mapSuccess(paymentData))
                    } else {
                        finishOnce(
                            if (activityResult.resultCode == Activity.RESULT_CANCELED) {
                                PaymentResult.Cancelled(raw = redact("cancelled", emptyMap()))
                            } else {
                                failure(FailureCode.GATEWAY_DECLINED, "Google Pay sheet did not complete")
                            },
                        )
                    }
                }

            paymentsClient
                .loadPaymentData(request)
                .addOnSuccessListener { paymentData -> finishOnce(mapSuccess(paymentData)) }
                .addOnFailureListener { exception ->
                    when (exception) {
                        is ResolvableApiException ->
                            runCatching {
                                launcher.launch(IntentSenderRequest.Builder(exception.resolution).build())
                            }.onFailure {
                                finishOnce(failure(FailureCode.SDK_ERROR, "Could not resolve Google Pay sheet"))
                            }
                        is ApiException -> {
                            AppLog.w("Google Pay declined: ${exception.statusCode}", exception, tag = TAG)
                            finishOnce(failure(FailureCode.GATEWAY_DECLINED, "Google Pay declined the request"))
                        }
                        else -> finishOnce(failure(FailureCode.SDK_ERROR, exception.message ?: "Google Pay error"))
                    }
                }
        }
    }

    private fun mapSuccess(paymentData: PaymentData?): PaymentResult {
        if (paymentData == null) return failure(FailureCode.SDK_ERROR, "Google Pay returned no payment data")
        val json = runCatching { paymentData.toJson() }.getOrNull()
        val tokenPresent = json?.contains("\"token\"") == true
        return PaymentResult.Success(
            paymentId = "gpay_${System.currentTimeMillis()}",
            verification = mapOf("has_token" to tokenPresent.toString()),
            raw = redact("success", mapOf("has_token" to tokenPresent.toString())),
        )
    }

    private fun failure(
        code: FailureCode,
        message: String,
    ): PaymentResult.Failure =
        PaymentResult.Failure(
            code = code,
            message = UiText.of(message),
            raw =
                redact(
                    "failure",
                    mapOf(
                        "error" to message,
                    ),
                ),
        )

    private fun redact(
        label: String,
        extra: Map<String, String>,
    ) = Redactor.redact("googlepay_$label", extra)

    private companion object {
        const val TAG = "GooglePayGateway"
    }
}
