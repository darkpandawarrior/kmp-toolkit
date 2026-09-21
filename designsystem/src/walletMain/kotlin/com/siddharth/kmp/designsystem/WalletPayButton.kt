package com.siddharth.kmp.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.siddharth.kmp.paymentsapi.WalletAvailability

/**
 * The device-wallet pay button: Apple's `PKPaymentButton` on iOS, Google's `PayButton` on Android.
 *
 * This is the one component in the design system that is deliberately NOT ours. Both vendors
 * mandate their own button asset in their brand guidelines, and both enforce it at review: a
 * hand-drawn "Pay with Apple Pay" mark is a store rejection, not a styling decision. So the
 * composable is a thin, gated wrapper and nothing about its appearance is themeable here.
 *
 * Declared in an android+ios intermediate source set rather than commonMain. Desktop and browser
 * have no wallet, so they get no declaration at all — an `expect` in commonMain would oblige them
 * to supply an actual, and the only actual they could supply is an empty one that draws nothing
 * while compiling perfectly.
 *
 * The [availability] gate lives here, once, rather than in each actual. It is the whole point of
 * the four-state flag: a button that is drawn and then fails is worse than no button, so anything
 * short of [WalletAvailability.AVAILABLE] renders nothing and the caller shows its own fallback
 * (a card form, or an "add a card to your wallet" hint for
 * [WalletAvailability.NO_CARDS_PROVISIONED]).
 *
 * @param allowedPaymentMethodsJson Google Pay's `allowedPaymentMethods` JSON array, as produced by
 *   `GooglePayRequestBuilder.isReadyToPayRequest()`. Required by `PayButton`, which renders the
 *   accepted networks inside the mark. **Ignored on iOS** — `PKPaymentButton` carries no request;
 *   Apple's networks are declared in the `PKPaymentRequest` at authorization time instead.
 */
@Composable
fun WalletPayButton(
    availability: WalletAvailability,
    allowedPaymentMethodsJson: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (availability != WalletAvailability.AVAILABLE) return
    PlatformWalletPayButton(
        allowedPaymentMethodsJson = allowedPaymentMethodsJson,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * The vendor button itself, already past the availability gate.
 *
 * Internal and ungated by design: keeping the gate in the public wrapper means a new platform
 * cannot forget it, which is the mistake a per-platform guard invites.
 */
@Composable
internal expect fun PlatformWalletPayButton(
    allowedPaymentMethodsJson: String,
    onClick: () -> Unit,
    modifier: Modifier,
)
