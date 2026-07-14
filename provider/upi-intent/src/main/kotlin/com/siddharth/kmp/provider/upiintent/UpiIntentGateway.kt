package com.siddharth.kmp.provider.upiintent

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.siddharth.kmp.paymentsapi.PendingReason
import com.siddharth.kmp.paymentsapi.PreparedPayment
import com.siddharth.kmp.paymentsapi.Redactor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Raw UPI **intent** gateway — no SDK, no partner onboarding. It constructs an NPCI `upi://pay`
 * deep link from backend-supplied fields and hands it to whichever UPI app the user picks via the
 * system chooser.
 *
 * ────────────────────────────────────────────────────────────────────────────────────────────────
 * ⚠️  THE UNVERIFIABILITY WARNING — the single most important thing this provider teaches:
 *
 * The `response` extra a UPI app returns to the calling Activity is **client-side and completely
 * unverifiable**. Any app (or a repackaged/malicious one) can return `Status=SUCCESS` without a
 * rupee moving. There is NO client-side signature and, for a personal VPA (`pa=` pointing at an
 * individual, not a registered merchant), there is NO transaction-status API to confirm against.
 *
 * Therefore a [PaymentResult.Success] from this gateway is a *hint only*. The server MUST confirm
 * the payment out-of-band (merchant webhook / bank statement / PSP reconciliation) before crediting
 * anything. We surface the client's claimed status honestly, tag every payload as redacted, and
 * rely on the orchestrator's server-confirm step to decide the truth. Never trust this result on
 * the client.
 * ────────────────────────────────────────────────────────────────────────────────────────────────
 */
class UpiIntentGateway : PaymentGateway {
    override val id: GatewayId = GatewayId("upi_intent")

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = "UPI (raw intent)",
            status = GatewayStatus.SANDBOX_READY,
            capabilities = setOf(Capability.ONE_TIME_PAYMENT, Capability.UPI),
            region = "India",
            docsPath = "docs/providers/upi-intent.md",
            blurb =
                "Constructs a raw upi:// deep link and launches the UPI app chooser — no SDK, no " +
                    "onboarding. The app's returned status is client-side and UNVERIFIABLE; the server " +
                    "must confirm every payment out-of-band before trusting it.",
        )

    /**
     * Build the `upi://pay` parameter map. The backend owns the trust-sensitive fields — payee
     * address (`pa`), payee name (`pn`) and merchant code (`mc`) all come from [CreatedOrder.providerParams]
     * so the client can never point the payment at itself. Amount is derived from the server-set
     * [CreatedOrder.order] amount (never client-chosen) and rendered as UPI's two-decimal string.
     */
    override suspend fun prepare(created: CreatedOrder): PreparedPayment {
        val backend = created.providerParams
        val pa =
            backend["pa"]
                ?: throw PaymentPreparationException("UPI order missing payee address (pa)")
        val pn =
            backend["pn"]
                ?: throw PaymentPreparationException("UPI order missing payee name (pn)")
        val mc = backend["mc"].orEmpty()

        val amount = created.order.amount
        if (amount.currency != "INR") {
            throw PaymentPreparationException("UPI only supports INR, was ${amount.currency}")
        }

        val params =
            linkedMapOf(
                "pa" to pa,
                "pn" to pn,
                "tr" to created.order.orderId, // transaction reference = our order id
                "am" to amount.toUpiAmountString(),
                "cu" to "INR",
                "mc" to mc,
            )

        AppLog.d("prepared UPI intent order=${created.order.orderId} am=${params["am"]}", tag = TAG)
        return PreparedPayment(
            gatewayId = id,
            orderId = created.order.orderId,
            amount = amount,
            params = params,
        )
    }

    override suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult {
        val androidHost =
            host as? AndroidPaymentHost
                ?: return PaymentResult.Failure(
                    code = FailureCode.SDK_ERROR,
                    message = UiText.of("UPI intent requires an Android host"),
                    raw = Redactor.redact(RAW_LABEL, mapOf("error" to "host_not_android")),
                )

        val uri = buildUpiUri(prepared.params)

        return suspendCancellableCoroutine { cont ->
            // Guard against a double resume: ActivityResult can fire once, but process recreation
            // or a defensive path could invoke the callback more than once — resume-once only.
            val resumed = AtomicBoolean(false)

            fun finishOnce(result: PaymentResult) {
                if (resumed.compareAndSet(false, true) && cont.isActive) {
                    cont.resume(result)
                }
            }

            val launcher =
                androidHost.registerForResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { activityResult: ActivityResult ->
                    finishOnce(parseUpiResult(activityResult))
                }

            // NPCI mandates the *generic* Android chooser for the upi:// intent — you must NOT
            // target a specific UPI package or pre-select an app. createChooser presents every
            // installed UPI app and satisfies that requirement.
            val payIntent = Intent(Intent.ACTION_VIEW, uri)
            val chooser = Intent.createChooser(payIntent, "Pay with UPI")

            try {
                launcher.launch(chooser)
            } catch (e: Exception) {
                AppLog.e("failed to launch UPI chooser", e, tag = TAG)
                finishOnce(
                    PaymentResult.Failure(
                        code = FailureCode.SDK_ERROR,
                        message = UiText.of("Could not open a UPI app"),
                        raw = Redactor.redact(RAW_LABEL, mapOf("error" to (e.message ?: "launch_failed"))),
                    ),
                )
            }
        }
    }

    private fun buildUpiUri(params: Map<String, String>): Uri {
        val builder = Uri.Builder().scheme("upi").authority("pay")
        params.forEach { (k, v) -> builder.appendQueryParameter(k, v) }
        return builder.build()
    }

    /**
     * Parse the UPI app's returned `response` extra. Shape (per NPCI):
     * `txnId=...&responseCode=...&Status=SUCCESS|FAILURE|SUBMITTED&txnRef=...`
     *
     * MANY apps return a `null` response (no extras) when the user backs out — that is treated as
     * [PaymentResult.Cancelled], not a failure.
     */
    internal fun parseUpiResult(activityResult: ActivityResult): PaymentResult {
        val response = activityResult.data?.getStringExtra("response")
        if (response.isNullOrBlank()) {
            AppLog.d(
                "UPI returned null/blank response (resultCode=${activityResult.resultCode}) → Cancelled",
                tag = TAG,
            )
            return PaymentResult.Cancelled(
                raw = Redactor.redact(RAW_LABEL, mapOf("response" to "null")),
            )
        }

        val fields = parseResponseString(response)
        val status = fields["Status"]?.uppercase().orEmpty()
        val txnId = fields["txnId"].orEmpty()
        val txnRef = fields["txnRef"].orEmpty()
        val responseCode = fields["responseCode"].orEmpty()

        // verification: unredacted server-bound fields. NB: even these are only *claims* — the
        // server verifies out-of-band; there is no client-side signature to check.
        val verification =
            mapOf(
                "txnId" to txnId,
                "txnRef" to txnRef,
                "responseCode" to responseCode,
                "Status" to status,
            )
        val raw =
            Redactor.redact(
                RAW_LABEL,
                mapOf(
                    "txnId" to txnId,
                    "txnRef" to txnRef,
                    "responseCode" to responseCode,
                    "Status" to status,
                ),
            )

        return when (status) {
            "SUCCESS" -> {
                // Surface the client's SUCCESS claim as Success so the orchestrator forwards
                // `verification` to the server-confirm step — which is the ONLY authority here.
                AppLog.i("UPI Status=SUCCESS (unverified client claim) txnId=$txnId", tag = TAG)
                PaymentResult.Success(paymentId = txnId, verification = verification, raw = raw)
            }

            "SUBMITTED" -> {
                AppLog.i("UPI Status=SUBMITTED → Pending(UPI_SUBMITTED)", tag = TAG)
                PaymentResult.Pending(
                    reason = PendingReason.UPI_SUBMITTED,
                    verification = verification,
                    raw = raw,
                )
            }

            "FAILURE" -> {
                AppLog.w("UPI Status=FAILURE responseCode=$responseCode", tag = TAG)
                PaymentResult.Failure(
                    code = FailureCode.GATEWAY_DECLINED,
                    message = UiText.of("UPI payment failed"),
                    raw = raw,
                )
            }

            else -> {
                // Unknown / empty status — treat as cancelled rather than a false success.
                AppLog.w("UPI unknown Status='$status' → Cancelled", tag = TAG)
                PaymentResult.Cancelled(raw = raw)
            }
        }
    }

    private fun parseResponseString(response: String): Map<String, String> =
        response
            .split("&")
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null else pair.substring(0, idx) to pair.substring(idx + 1)
            }.toMap()

    private companion object {
        const val TAG = "UpiIntentGateway"
        const val RAW_LABEL = "upi_intent_response"
    }
}

/**
 * Render minor units to UPI's canonical two-decimal string, e.g. 1050 paise → "10.50".
 * Presentation concern — kept at the provider edge, never in the [com.siddharth.kmp.paymentsapi.Money] type.
 */
private fun com.siddharth.kmp.paymentsapi.Money.toUpiAmountString(): String {
    val rupees = amountMinor / 100
    val paise = amountMinor % 100
    return "%d.%02d".format(rupees, paise)
}
