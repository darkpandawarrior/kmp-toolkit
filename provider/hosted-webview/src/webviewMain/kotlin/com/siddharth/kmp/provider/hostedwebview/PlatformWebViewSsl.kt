package com.siddharth.kmp.provider.hostedwebview

import com.multiplatform.webview.web.PlatformWebViewParams

/**
 * Builds the platform SSL-fail-closed hook for [HostedCheckoutScreen]'s WebView, or `null` on
 * platforms where [PlatformWebViewParams] has nothing to attach a client to.
 *
 * This is the `expect` seam that keeps commonMain free of platform-only WebView types —
 * `compose-webview-multiplatform`'s `PlatformWebViewParams` is itself an `expect class` whose actual
 * shape differs per target (Android's carries a `client: AccompanistWebViewClient?`; iOS's is empty),
 * so the SSL-fail-closed client can only be constructed inside each platform's own `actual`.
 */
expect fun sslFailClosedWebViewParams(onSslError: () -> Unit): PlatformWebViewParams?
