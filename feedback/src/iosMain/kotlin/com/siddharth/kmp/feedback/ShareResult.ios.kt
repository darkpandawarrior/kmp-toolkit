package com.siddharth.kmp.feedback

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/**
 * iOS actual: UIActivityViewController, the counterpart to Android's ACTION_SEND chooser.
 *
 * This was `actual fun shareText(text: String) {}` — an empty body with no capability flag, so an
 * iOS user got a share button that did nothing and the caller had no way to detect it. jvmMain and
 * wasmJsMain keep their empty bodies because no share sheet exists on those platforms; iOS has one,
 * which made the no-op a false claim rather than an honest gap.
 *
 * Ported from Doori's `IosShareSheet` (core/platform), which is the same 24 lines already running
 * in the family.
 */
actual fun shareText(text: String) {
    presentActivitySheet(listOf(text))
}

/**
 * Presents [items] in the system share sheet. Shared by [shareText] and [shareImage] so the
 * top-view-controller walk exists once.
 *
 * Never throws: every caller is a share action whose fallback is to do nothing visible. A null
 * top view controller (no key window yet) simply means there is nothing to present from.
 *
 * On iPad this is presented as a popover, and UIKit raises `NSInvalidArgumentException` unless the
 * popover has an anchor. Consumers in this family ship `TARGETED_DEVICE_FAMILY = "1,2"`, so that
 * path is reachable and unanchored presentation is a crash, not a cosmetic issue. Anchoring to the
 * presenting controller's own view is the minimum that avoids it.
 */
internal fun presentActivitySheet(items: List<Any>) {
    if (items.isEmpty()) return
    val top = topViewController() ?: return
    val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
    controller.popoverPresentationController?.sourceView = top.view
    top.presentViewController(controller, animated = true, completion = null)
}

/**
 * The view controller actually on screen. Presenting from the root controller while a modal is up
 * throws at runtime, so walk the presentation chain to its end first.
 */
private fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) {
        top = top.presentedViewController
    }
    return top
}
