package com.siddharth.kmp.provider.omise

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import co.omise.android.ui.CreditCardActivity
import co.omise.android.ui.OmiseActivity
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Omise — Tier-1, real Android SDK (`co.omise:omise-android:5.6.0`, on Maven Central — unlike
 * Square). Unlike Square's legacy SDK, Omise's `CreditCardActivity` already speaks
 * `ActivityResultContract` natively (its own README recommends `registerForActivityResult`), so this
 * rides `AndroidPaymentHost.registerForResult` directly — no relay/legacy-bridge needed.
 *
 * The backend decides real vs mock via a configured Omise test public/secret key pair (same
 * auto-degrade pattern as the other gateways) and hands the (non-secret, client-embeddable) public
 * key down through [CreatedOrder.providerParams].
 *
 * **Real** (public key present): launches `CreditCardActivity` with `EXTRA_PKEY`; the resulting
 * token (`EXTRA_TOKEN`) becomes `PaymentResult.Success.paymentId`. The backend's `verify()` charges
 * it server-side (`POST /charges`) using the secret key, which never leaves the backend.
 *
 * **Mock** (public key absent): [SimulatedPayment] — no live sandbox credentials configured, so this
 * ships `MOCK_MODE` by default.
 *
 * `docs: https://www.omise.co/tokens-api`
 */
class OmiseGateway : PaymentGateway {
    override val id: GatewayId = GatewayId("omise")

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = "Omise",
            status = GatewayStatus.MOCK_MODE,
            capabilities = setOf(Capability.ONE_TIME_PAYMENT, Capability.CARDS),
            region = "SEA",
            docsPath = "docs/providers/omise.md",
            blurb =
                "Omise's real Android SDK — native CreditCardActivity tokenization when a sandbox " +
                    "public key is configured backend-side, mock fallback otherwise.",
        )

    override suspend fun prepare(created: CreatedOrder): PreparedPayment {
        val publicKey = created.providerParams[KEY_PUBLIC_KEY]
        return PreparedPayment(
            gatewayId = id,
            orderId = created.order.orderId,
            amount = created.order.amount,
            params = if (publicKey.isNullOrBlank()) emptyMap() else mapOf(KEY_PUBLIC_KEY to publicKey),
        )
    }

    override suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult {
        val publicKey = prepared.params[KEY_PUBLIC_KEY]
        if (publicKey.isNullOrBlank()) return SimulatedPayment.run(id, prepared)

        val androidHost =
            host as? AndroidPaymentHost
                ?: return failure(FailureCode.SDK_ERROR, "Omise requires an Android host")

        return suspendCancellableCoroutine { cont ->
            val resumed = AtomicBoolean(false)

            fun finishOnce(result: PaymentResult) {
                if (resumed.compareAndSet(false, true) && cont.isActive) cont.resume(result) { _, _, _ -> }
            }

            val launcher =
                androidHost.registerForResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
                    when {
                        activityResult.resultCode == Activity.RESULT_CANCELED ->
                            finishOnce(PaymentResult.Cancelled(raw = redact("cancelled", emptyMap())))
                        else -> {
                            val token = activityResult.data?.getStringExtra(OmiseActivity.EXTRA_TOKEN)
                            if (token.isNullOrBlank()) {
                                finishOnce(failure(FailureCode.GATEWAY_DECLINED, "Omise returned no token"))
                            } else {
                                finishOnce(
                                    PaymentResult.Success(
                                        paymentId = token,
                                        verification = emptyMap(),
                                        raw = redact("success", emptyMap()),
                                    ),
                                )
                            }
                        }
                    }
                }

            val intent =
                Intent(androidHost.activity, CreditCardActivity::class.java)
                    .putExtra(OmiseActivity.EXTRA_PKEY, publicKey)
            launcher.launch(intent)
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
    ) = Redactor.redact("omise_$label", extra)

    private companion object {
        const val KEY_PUBLIC_KEY = "public_key"
    }
}
