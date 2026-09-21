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

/**
 * Whether *this platform's* [sslFailClosedWebViewParams] actually enforces fail-closed TLS handling
 * on the checkout WebView. True on Android, false on iOS.
 *
 * Public API rather than a comment on purpose. The iOS `actual` of
 * [sslFailClosedWebViewParams] returns `null` and argues in its KDoc that WKWebView fails closed by
 * default — which may well be true, but it is an assumption about a third party's behaviour, not
 * something this module checks, enforces or can promise. A caller with a payment session to protect
 * needs to be able to read that difference at runtime and decide, exactly the way `canShareImage()`
 * in :feedback lets a caller decide rather than hope.
 *
 * What a caller does with a `false` is its policy, not this module's: refuse to open the webview,
 * fall back to a native SDK, or accept the risk and log it. What it must not have to do is read
 * source to discover the guarantee is not there.
 *
 * ```kotlin
 * if (!sslFailClosedSupported()) return PaymentResult.Unavailable("tls_unenforced")
 * HostedCheckoutScreen(checkoutUrl, matchReturn, onResult)
 * ```
 */
expect fun sslFailClosedSupported(): Boolean
