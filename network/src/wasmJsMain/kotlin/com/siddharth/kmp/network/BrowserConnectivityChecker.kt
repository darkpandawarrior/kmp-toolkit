package com.siddharth.kmp.network

/**
 * Real reachability on wasmJs, read straight from `navigator.onLine`.
 *
 * Before this, wasmJs fell through to [AlwaysOnlineConnectivityChecker], which returns `true`
 * without checking anything. That is the dishonest case the rest of this module avoids: a caller
 * gating a sync on [isOnline] was told "yes" on a machine with no network at all, then failed at
 * the HTTP call with a worse error than it needed.
 *
 * **No dependency was added for this, deliberately.** `dev.jordond.connectivity`'s device artifact
 * publishes no wasm target, and reaching `window` the usual way would mean pulling in
 * `kotlinx-browser` — a whole DOM library for one boolean, on a target that today is only a
 * preview. One `@JsFun` is the smaller answer.
 *
 * [canObserveConnectivity] stays `false` and [observeIsOnline] keeps the single-shot default. The
 * browser does fire `online`/`offline` events, but subscribing to them from Kotlin/Wasm needs the
 * DOM library this class exists to avoid. Reporting `false` is the honest position: `offline-outbox`
 * then keeps its timer retry here rather than subscribing to a Flow that never emits again.
 */
class BrowserConnectivityChecker : ConnectivityChecker {
    override fun isOnline(): Boolean = navigatorOnLine()
}

/**
 * `navigator.onLine`. The known limit is the browser's, not ours: it reports whether the browser
 * has *a* network interface, not whether the internet is reachable through it, so a captive portal
 * reads as online. Still strictly better than assuming online unconditionally, and it is the same
 * guarantee every web app has.
 */
@JsFun("() => navigator.onLine")
private external fun navigatorOnLine(): Boolean
