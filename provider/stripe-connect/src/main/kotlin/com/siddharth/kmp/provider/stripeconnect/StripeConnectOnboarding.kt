package com.siddharth.kmp.provider.stripeconnect

import com.siddharth.kmp.common.AppLog
import com.siddharth.kmp.paymentsapi.ConnectAccount
import com.siddharth.kmp.paymentsapi.ConnectAccountStatus
import com.siddharth.kmp.paymentsapi.ConnectBackend
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.PayoutSnapshot
import com.siddharth.kmp.provider.hostedwebview.HostedCheckoutRelay
import com.siddharth.kmp.provider.hostedwebview.HostedReturnOutcome
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Not a [com.siddharth.kmp.paymentsapi.PaymentGateway] — Connect onboards a payout destination, it
 * doesn't pay an order. Shared with [com.siddharth.kmp.provider.stripeconnect.StripeConnectCheckoutHost]
 * so both sides of the relay key on the same id.
 */
val STRIPE_CONNECT_GATEWAY_ID = GatewayId("stripe_connect")

/**
 * Client-side Stripe Connect payout onboarding: mock hosted OAuth (via the shared
 * [HostedCheckoutRelay] + `HostedCheckoutHost` composable mounted by the consuming app) to connect a
 * payout account, then a payout to that account over the existing payout rail ([ConnectBackend.payout]).
 *
 * This is deliberately NOT a `PaymentGateway` — [com.siddharth.kmp.paymentsapi.PaymentGateway.pay]
 * models paying FOR an order; Connect onboarding has no order at all, it's a one-time destination
 * setup. Modeled as its own service so the consuming app calls it directly rather than forcing it
 * through the order/pay contract.
 *
 * `status = MOCK_MODE`: a real Connect OAuth/KYC flow is partner-gated — this always resolves through
 * the mock hosted-OAuth callback, never a live redirect.
 *
 * NOT the same module as `:provider:stripe` (Stripe PaymentSheet, a native-SDK archetype-A gateway) —
 * Connect is a payout-onboarding service, unrelated to that module's checkout flow.
 */
class StripeConnectOnboarding(
    private val backend: ConnectBackend,
    private val relay: HostedCheckoutRelay,
) {
    /**
     * Starts onboarding, opens the mock hosted OAuth in the shared WebView relay, and suspends until
     * the return-URL fires. The mock consent page's "Authorize" link is itself the OAuth callback, so
     * by the time the WebView lands there the account is already CONNECTED server-side; this just
     * polls [ConnectBackend.status] to read that back.
     */
    suspend fun onboard(): ConnectAccount {
        val onboarding = backend.onboard()
        AppLog.d("onboarding started id=${onboarding.onboardingId}", tag = TAG)

        val outcome =
            suspendCancellableCoroutine { cont ->
                relay.register(STRIPE_CONNECT_GATEWAY_ID) { result ->
                    if (cont.isActive) cont.resume(result) { _, _, _ -> }
                }
                cont.invokeOnCancellation { relay.clear(STRIPE_CONNECT_GATEWAY_ID) }
                relay.launch(STRIPE_CONNECT_GATEWAY_ID, onboarding.hostedOAuthUrl)
            }

        return when (outcome) {
            is HostedReturnOutcome.Success -> backend.status(onboarding.accountId)
            is HostedReturnOutcome.Failure, HostedReturnOutcome.Cancelled ->
                ConnectAccount(accountId = onboarding.accountId, status = ConnectAccountStatus.ONBOARDING_PENDING)
        }
    }

    /** Pays out to a connected account. Throws (via [ConnectBackend]) if the account isn't CONNECTED. */
    suspend fun payout(
        accountId: String,
        amount: Money,
        idempotencyKey: String,
    ): PayoutSnapshot = backend.payout(accountId, amount, idempotencyKey)

    /** Poll onboarding status. */
    suspend fun status(accountId: String): ConnectAccount = backend.status(accountId)

    private companion object {
        const val TAG = "StripeConnectOnboarding"
    }
}
