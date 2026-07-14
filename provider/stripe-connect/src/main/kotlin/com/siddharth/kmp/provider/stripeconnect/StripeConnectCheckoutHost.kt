package com.siddharth.kmp.provider.stripeconnect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.siddharth.kmp.provider.hostedwebview.HostedCheckoutRelay
import com.siddharth.kmp.provider.hostedwebview.HostedCheckoutScreen
import com.siddharth.kmp.provider.hostedwebview.HostedReturnOutcome

/**
 * Same idea as `provider:hosted-webview`'s `HostedCheckoutHost`, sized for Connect's one gateway id
 * instead of a whole [com.siddharth.kmp.provider.hostedwebview.HostedGatewayConfig] list — the mock
 * consent page's "Authorize"/"Cancel" links land on `/mock/connect/{id}/complete` or
 * `/mock/connect/cancelled`, and by the time the WebView observes either, the server-side transition
 * has already happened (or been declined); [StripeConnectOnboarding] just needs to know which one fired.
 */
@Composable
fun StripeConnectCheckoutHost(
    relay: HostedCheckoutRelay,
    modifier: Modifier = Modifier,
) {
    val request by relay.requests.collectAsState()
    val activeRequest = request ?: return
    if (activeRequest.gatewayId != STRIPE_CONNECT_GATEWAY_ID) return

    HostedCheckoutScreen(
        checkoutUrl = activeRequest.checkoutUrl,
        matchReturn = ::matchConnectReturn,
        onResult = { outcome -> relay.reportResult(STRIPE_CONNECT_GATEWAY_ID, outcome) },
        modifier = modifier,
    )
}

private fun matchConnectReturn(url: String): HostedReturnOutcome? =
    when {
        url.contains("/mock/connect/cancelled") -> HostedReturnOutcome.Cancelled
        url.contains("/complete") -> HostedReturnOutcome.Success(paymentId = null)
        else -> null
    }
