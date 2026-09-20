@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.siddharth.kmp.provider.applepay

import com.siddharth.kmp.common.UiText
import com.siddharth.kmp.common.minorToDecimalString
import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.CreatedOrder
import com.siddharth.kmp.paymentsapi.FailureCode
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayMeta
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.IosPaymentHost
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PreparedPayment
import com.siddharth.kmp.paymentsapi.Redactor
import com.siddharth.kmp.paymentsapi.WalletAvailability
import com.siddharth.kmp.paymentsapi.WalletGateway
import com.siddharth.kmp.paymentsapi.WalletMerchantConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDecimalNumber
import platform.PassKit.PKMerchantCapability3DS
import platform.PassKit.PKPayment
import platform.PassKit.PKPaymentAuthorizationController
import platform.PassKit.PKPaymentAuthorizationControllerDelegateProtocol
import platform.PassKit.PKPaymentAuthorizationResult
import platform.PassKit.PKPaymentAuthorizationStatusSuccess
import platform.PassKit.PKPaymentRequest
import platform.PassKit.PKPaymentSummaryItem
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Apple Pay, through PassKit directly — there is no Apple Pay SDK to depend on, `PKPaymentRequest`
 * and `PKPaymentAuthorizationController` are the integration.
 *
 * Apple Pay does not settle money. It returns an encrypted [WalletToken] that a PSP with the
 * matching payment-processing certificate decrypts and charges, which is why this sits behind the
 * same `prepare`/`pay` split as every card gateway: the backend still creates the order, and a
 * client-side success here is still only a hint.
 *
 * Ships [GatewayStatus.KYC_GATED] and reports [WalletAvailability.NOT_CONFIGURED] until a real
 * `merchant.*` identifier replaces [APPLE_MERCHANT_ID_SENTINEL]. A merchant id needs an Apple
 * Developer Program membership, so there is no solo-runnable sandbox to pretend otherwise about.
 *
 * `docs: https://developer.apple.com/documentation/passkit/pkpaymentauthorizationcontroller`
 */
class ApplePayGateway(
    private val config: ApplePayConfig = ApplePayConfig(),
) : WalletGateway {
    override val id: GatewayId = GatewayId("applepay")

    override val merchantConfig: WalletMerchantConfig = config.merchantConfig

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = "Apple Pay",
            status = GatewayStatus.KYC_GATED,
            capabilities = setOf(Capability.ONE_TIME_PAYMENT, Capability.WALLET, Capability.CARDS),
            region = "Global",
            docsPath = "docs/providers/applepay.md",
            blurb =
                "Apple Pay via PassKit directly — a wallet method that returns an encrypted token " +
                    "for a PSP to charge. Needs an Apple-issued merchant id, so it is KYC-gated.",
        )

    /**
     * Whether the sheet can be presented, asked before anything is drawn.
     *
     * The order of the three checks is the whole point. Config first, so an unprovisioned build
     * never touches PassKit at all; then hardware/OS; then cards. Collapsing these into one boolean
     * is how a device with no card on file becomes indistinguishable from a broken build.
     */
    override suspend fun availability(host: PaymentHost): WalletAvailability {
        if (!config.isProvisioned) return WalletAvailability.NOT_CONFIGURED
        val networks = config.supportedNetworks.mapNotNull(::toPKPaymentNetwork)
        if (networks.isEmpty()) return WalletAvailability.NOT_CONFIGURED
        if (!PKPaymentAuthorizationController.canMakePayments()) {
            return WalletAvailability.UNSUPPORTED_DEVICE
        }
        return if (PKPaymentAuthorizationController.canMakePaymentsUsingNetworks(networks)) {
            WalletAvailability.AVAILABLE
        } else {
            WalletAvailability.NO_CARDS_PROVISIONED
        }
    }

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
        val iosHost =
            host as? IosPaymentHost
                ?: return failure(FailureCode.SDK_ERROR, "Apple Pay requires an iOS host")
        if (!config.isProvisioned) {
            return failure(FailureCode.CONFIG_MISSING, "Apple Pay merchant id is not provisioned")
        }

        val controller = PKPaymentAuthorizationController(paymentRequest = buildRequest(prepared.amount))
        var release: (() -> Unit)? = null
        try {
            return suspendCancellableCoroutine { continuation ->
                // Plain `var`, no atomic: every PassKit delegate callback is delivered on the main
                // thread, so didAuthorize and didFinish cannot race each other. They do both fire,
                // in that order, which is the only reason a guard is needed at all.
                var authorized: PaymentResult? = null
                var resumed = false

                fun finishOnce(result: PaymentResult) {
                    if (!resumed && continuation.isActive) {
                        resumed = true
                        continuation.resume(result)
                    }
                }

                val delegate =
                    AuthorizationDelegate(
                        onAuthorized = { payment -> authorized = success(payment) },
                        // didFinish is the ONLY terminal signal. A user who dismisses the sheet
                        // without authorizing reaches here with `authorized` still null - that is
                        // Cancelled, and there is no separate "cancelled" callback to observe.
                        onFinished = { finishOnce(authorized ?: cancelled()) },
                    )

                // Must happen BEFORE `delegate =` below: the property is weak, and the local
                // `delegate` above goes out of scope the moment this lambda returns.
                release = iosHost.retainForPayment(delegate)
                controller.delegate = delegate
                controller.presentWithCompletion { presented ->
                    if (!presented) {
                        finishOnce(failure(FailureCode.SDK_ERROR, "Apple Pay sheet could not be presented"))
                    }
                }

                continuation.invokeOnCancellation { controller.dismissWithCompletion(null) }
            }
        } finally {
            // In a finally, so cancellation releases the delegate too. Skipping this leaks one
            // delegate per payment attempt for the lifetime of the host.
            release?.invoke()
        }
    }

    private fun buildRequest(amount: Money): PKPaymentRequest =
        PKPaymentRequest().apply {
            merchantIdentifier = config.merchantId
            countryCode = config.countryCode
            currencyCode = amount.currency
            merchantCapabilities = PKMerchantCapability3DS
            supportedNetworks = config.supportedNetworks.mapNotNull(::toPKPaymentNetwork)
            // Apple prices in major units and takes a decimal string, never a Double: NSDecimalNumber
            // is exact, and `amountMinor / 100.0` is where a cent goes missing on a 1/3 split.
            paymentSummaryItems =
                listOf(
                    PKPaymentSummaryItem.summaryItemWithLabel(
                        label = config.merchantName,
                        amount = NSDecimalNumber(string = amount.amountMinor.minorToDecimalString()),
                    ),
                )
        }

    private fun success(payment: PKPayment): PaymentResult {
        val token = payment.toWalletToken()
        return PaymentResult.Success(
            paymentId = payment.token.transactionIdentifier,
            // The encrypted blob goes to the backend for the PSP to decrypt. It is NOT in `raw` —
            // `raw` is the human-readable, secret-safe view.
            verification = mapOf("apple_payment_data" to token.paymentData),
            raw = redact("success", mapOf("network" to (token.network?.name ?: "unknown"))),
        )
    }

    private fun cancelled(): PaymentResult = PaymentResult.Cancelled(raw = redact("cancelled", emptyMap()))

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
    ) = Redactor.redact("applepay_$label", extra)
}

/**
 * Retains nothing itself and is retained by the host — see `IosPaymentHost.retainForPayment` for why
 * that matters. Subclasses `NSObject` because PassKit will only talk to a real Objective-C object.
 */
private class AuthorizationDelegate(
    private val onAuthorized: (PKPayment) -> Unit,
    private val onFinished: () -> Unit,
) : NSObject(),
    PKPaymentAuthorizationControllerDelegateProtocol {
    override fun paymentAuthorizationController(
        controller: PKPaymentAuthorizationController,
        didAuthorizePayment: PKPayment,
        handler: (PKPaymentAuthorizationResult?) -> Unit,
    ) {
        onAuthorized(didAuthorizePayment)
        // Always Success here. The real accept/decline happens server-side against the PSP; showing
        // the user a red X for a payment the backend has not judged yet would be a lie, and PassKit
        // gives no way to revise this once the sheet closes.
        handler(
            PKPaymentAuthorizationResult(
                status = PKPaymentAuthorizationStatusSuccess,
                errors = null,
            ),
        )
    }

    override fun paymentAuthorizationControllerDidFinish(controller: PKPaymentAuthorizationController) {
        controller.dismissWithCompletion(null)
        onFinished()
    }
}
