package com.siddharth.kmp.paymentsapi

import platform.UIKit.UIViewController

/**
 * iOS realization of [PaymentHost], the peer of `AndroidPaymentHost`.
 *
 * Android's problem is that gateway SDKs are Activity-and-callback shaped; iOS's problem is
 * different and nastier, so this interface is not a mirror-image of the Android one. It carries the
 * two things an iOS gateway cannot get for itself: something to present a sheet *from*, and
 * somewhere for a delegate to live while that sheet is up.
 */
interface IosPaymentHost : PaymentHost {
    /**
     * The view controller a provider presents its sheet from.
     *
     * A function rather than a property because the correct answer changes across a payment — the
     * topmost presented controller at the moment of the call is not the one that existed when the
     * host was built. Providers call it at presentation time and never store the result, which is
     * the iOS equivalent of `AndroidPaymentHost` exposing the activity instead of capturing it.
     */
    fun presentationViewController(): UIViewController

    /**
     * Keep [delegate] alive for the duration of one payment; the returned lambda releases it.
     *
     * LOAD-BEARING, not a convenience. `PKPaymentAuthorizationController.setDelegate` — like most
     * UIKit delegate and target properties — holds its delegate **weakly**. A delegate allocated
     * inside a `suspend fun` has no other strong referrer, so ARC frees it as soon as the call
     * frame that created it suspends. The sheet then stays on screen with a dead delegate: no
     * authorization callback ever fires, the coroutine never resumes, and the payment hangs forever
     * with no error to report. It is invisible in a debug build with a breakpoint holding the frame,
     * and reproducible in release.
     *
     * The host owns the strong reference so a provider cannot leak it across view-controller
     * recreation — the same lifetime discipline as `AndroidPaymentHost.registerForResult`, which
     * owns registration/unregistration rather than handing a provider the registry.
     *
     * Call the returned lambda in a `finally`, so cancellation releases it too.
     */
    fun retainForPayment(delegate: Any): () -> Unit
}
