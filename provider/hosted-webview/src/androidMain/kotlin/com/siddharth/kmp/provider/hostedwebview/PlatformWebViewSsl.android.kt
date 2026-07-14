package com.siddharth.kmp.provider.hostedwebview

import com.multiplatform.webview.web.PlatformWebViewParams

actual fun sslFailClosedWebViewParams(onSslError: () -> Unit): PlatformWebViewParams? =
    PlatformWebViewParams(client = SslFailClosedWebViewClient(onSslError))
