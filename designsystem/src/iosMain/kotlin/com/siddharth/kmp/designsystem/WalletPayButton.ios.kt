@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.siddharth.kmp.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSSelectorFromString
import platform.PassKit.PKPaymentButton
import platform.PassKit.PKPaymentButtonStyleAutomatic
import platform.PassKit.PKPaymentButtonTypeBuy
import platform.UIKit.UIControlEventTouchUpInside
import platform.darwin.NSObject

/**
 * Apple's `PKPaymentButton`, hosted in Compose through `UIKitView`.
 *
 * Not drawable any other way. Apple's Human Interface Guidelines require the supplied button, and
 * App Review enforces it — so this reaches out to UIKit rather than composing anything.
 *
 * `PKPaymentButtonStyleAutomatic` follows the system light/dark appearance on its own, which is why
 * nothing here reads the Compose theme: overriding it would be the wrong answer AND off-guideline.
 */
@Composable
internal actual fun PlatformWalletPayButton(
    allowedPaymentMethodsJson: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    // `remember` is the strong reference, and it is load-bearing for the same reason
    // IosPaymentHost.retainForPayment is: UIControl holds its target WEAKLY. A target allocated in
    // the factory lambda below has no other referrer, so ARC frees it on the next composition and
    // the button goes quietly dead — it still draws, still animates on press, and never calls back.
    val target = remember { WalletButtonTapTarget() }
    // Reassigned rather than keyed into remember(), so a new lambda on recomposition does not
    // recreate the UIView (which would drop the target registration with it).
    target.onClick = onClick

    UIKitView(
        factory = {
            PKPaymentButton(
                paymentButtonType = PKPaymentButtonTypeBuy,
                paymentButtonStyle = PKPaymentButtonStyleAutomatic,
            ).apply {
                addTarget(
                    target = target,
                    action = NSSelectorFromString("handleTap"),
                    forControlEvents = UIControlEventTouchUpInside,
                )
            }
        },
        // PKPaymentButton has no intrinsic height Compose can measure through the interop boundary,
        // so it gets an explicit one. 48dp clears Apple's 30pt minimum and matches the house
        // primary-button height.
        modifier = modifier.fillMaxWidth().height(APPLE_PAY_BUTTON_HEIGHT.dp),
    )
}

private const val APPLE_PAY_BUTTON_HEIGHT = 48

/** A real Objective-C object, because `addTarget:action:` will not accept a Kotlin lambda. */
private class WalletButtonTapTarget : NSObject() {
    var onClick: () -> Unit = {}

    @ObjCAction
    fun handleTap() {
        onClick()
    }
}
