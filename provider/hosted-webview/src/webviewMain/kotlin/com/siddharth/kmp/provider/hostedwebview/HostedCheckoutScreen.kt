package com.siddharth.kmp.provider.hostedwebview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState

/**
 * Renders one hosted gateway's checkout page and watches every navigation for the return-URL
 * pattern. `docs: https://github.com/KevinnZou/compose-webview-multiplatform` — `state.lastLoadedUrl`
 * is the interception point; the moment [matchReturn] recognizes a redirect as terminal, [onResult]
 * fires exactly once (the `reported` guard survives re-navigation inside the same page load).
 *
 * SECURITY: SSL/certificate errors fail closed via [sslFailClosedWebViewParams] — its Android `actual`
 * cancels the load and reports [HostedReturnOutcome.Failure] through [onResult]; never call
 * `handler.proceed()` — a flaky regional cert is not a reason to bypass certificate validation on a
 * checkout page. See that function's KDoc for the iOS parity gap.
 */
@Composable
fun HostedCheckoutScreen(
    checkoutUrl: String,
    matchReturn: (String) -> HostedReturnOutcome?,
    onResult: (HostedReturnOutcome) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberWebViewState(checkoutUrl)
    val navigator = rememberWebViewNavigator()
    var reported by remember { mutableStateOf(false) }

    fun reportOnce(outcome: HostedReturnOutcome) {
        if (reported) return
        reported = true
        onResult(outcome)
    }

    LaunchedEffect(state.lastLoadedUrl) {
        val url = state.lastLoadedUrl ?: return@LaunchedEffect
        matchReturn(url)?.let { reportOnce(it) }
    }

    val platformParams =
        remember {
            sslFailClosedWebViewParams(onSslError = { reportOnce(HostedReturnOutcome.Failure("ssl_error")) })
        }

    WebView(
        state = state,
        navigator = navigator,
        modifier = modifier.fillMaxSize(),
        platformWebViewParams = platformParams,
    )
}

/**
 * Subscribes to [HostedCheckoutRelay.requests] and renders [HostedCheckoutScreen] for whichever
 * hosted gateway is currently mid-checkout; reports the outcome back through the same relay so
 * [HostedWebViewGateway.pay] resumes. `configs` is the app's assembled list of [HostedGatewayConfig].
 */
@Composable
fun HostedCheckoutHost(
    relay: HostedCheckoutRelay,
    configs: List<HostedGatewayConfig>,
    modifier: Modifier = Modifier,
) {
    val request by relay.requests.collectAsState()
    val activeRequest = request ?: return
    val config = configs.firstOrNull { it.gatewayId == activeRequest.gatewayId } ?: return

    HostedCheckoutScreen(
        checkoutUrl = activeRequest.checkoutUrl,
        matchReturn = config.matchReturn,
        onResult = { outcome -> relay.reportResult(activeRequest.gatewayId, outcome) },
        modifier = modifier,
    )
}
