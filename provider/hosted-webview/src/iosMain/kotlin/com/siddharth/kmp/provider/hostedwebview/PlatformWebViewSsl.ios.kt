package com.siddharth.kmp.provider.hostedwebview

import com.multiplatform.webview.web.PlatformWebViewParams

// ponytail: compose-webview-multiplatform 2.0.3's iOS `actual class PlatformWebViewParams` is empty —
// no client hook to attach an SSL-fail-closed override to (unlike Android's AccompanistWebViewClient
// seam). SSL-fail-closed isn't enforceable from this module on iOS yet; WKWebView already fails a
// broken chain by default (no `handler.proceed()` equivalent exists to accidentally call), so this is
// a silent-parity gap, not a proceed-on-error hole. Upgrade path: wire a WKNavigationDelegate once the
// library exposes an iOS SSL-error callback on PlatformWebViewParams.
actual fun sslFailClosedWebViewParams(onSslError: () -> Unit): PlatformWebViewParams? = null
