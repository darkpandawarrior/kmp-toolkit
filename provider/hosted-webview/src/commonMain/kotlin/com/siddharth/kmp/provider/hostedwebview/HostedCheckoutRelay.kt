package com.siddharth.kmp.provider.hostedwebview

import com.siddharth.kmp.paymentsapi.GatewayId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One hosted gateway asking to be shown as a checkout WebView. */
data class HostedCheckoutRequest(
    val gatewayId: GatewayId,
    val checkoutUrl: String,
)

/**
 * Bridges [HostedWebViewGateway.pay] (a suspending call with no UI of its own) to the Compose WebView
 * screen that actually renders the checkout and detects the return-URL. One relay instance is shared
 * (Koin single) across every hosted-webview config, keyed by [GatewayId] rather than single-slot like
 * a per-gateway relay — many configs share this one module.
 *
 * Flow: [HostedWebViewGateway.pay] registers a listener and calls [launch]; whichever composable is
 * observing [requests] renders the checkout for that gateway and calls [reportResult] once
 * `matchReturn` fires; `pay` resumes with the mapped [com.siddharth.kmp.paymentsapi.PaymentResult].
 */
class HostedCheckoutRelay {
    private val listeners = mutableMapOf<GatewayId, (HostedReturnOutcome) -> Unit>()
    private val _requests = MutableStateFlow<HostedCheckoutRequest?>(null)
    val requests: StateFlow<HostedCheckoutRequest?> = _requests

    fun register(
        gatewayId: GatewayId,
        onResult: (HostedReturnOutcome) -> Unit,
    ) {
        listeners[gatewayId] = onResult
    }

    fun launch(
        gatewayId: GatewayId,
        checkoutUrl: String,
    ) {
        _requests.value = HostedCheckoutRequest(gatewayId, checkoutUrl)
    }

    /** Called by the checkout screen once `matchReturn` detects a terminal redirect. */
    fun reportResult(
        gatewayId: GatewayId,
        outcome: HostedReturnOutcome,
    ) {
        listeners[gatewayId]?.invoke(outcome)
        clear(gatewayId)
    }

    fun clear(gatewayId: GatewayId) {
        listeners.remove(gatewayId)
        if (_requests.value?.gatewayId == gatewayId) _requests.value = null
    }
}
