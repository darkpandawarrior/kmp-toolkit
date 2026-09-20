package com.siddharth.kmp.provider.hostedwebview

import com.multiplatform.webview.web.PlatformWebViewParams

// ponytail: compose-webview-multiplatform 2.0.3's iOS `actual class PlatformWebViewParams` is empty —
// no client hook to attach an SSL-fail-closed override to (unlike Android's AccompanistWebViewClient
// seam). SSL-fail-closed isn't enforceable from this module on iOS yet; WKWebView already fails a
// broken chain by default (no `handler.proceed()` equivalent exists to accidentally call), so this is
// a silent-parity gap, not a proceed-on-error hole. Upgrade path: wire a WKNavigationDelegate once the
// library exposes an iOS SSL-error callback on PlatformWebViewParams.
//
// That WKWebView argument is an assumption about a third party, not a check this module performs, so
// it no longer lives only in a comment: sslFailClosedSupported() returns false here and a caller can
// act on it. See its KDoc in webviewMain.
actual fun sslFailClosedWebViewParams(onSslError: () -> Unit): PlatformWebViewParams? = null

/**
 * False. Nothing in this module installs or verifies TLS handling on the iOS WebView, so there is
 * no guarantee here to report as kept — only an expectation of WKWebView's defaults.
 */
actual fun sslFailClosedSupported(): Boolean = false
