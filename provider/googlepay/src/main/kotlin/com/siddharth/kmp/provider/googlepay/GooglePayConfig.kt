package com.siddharth.kmp.provider.googlepay

import com.google.android.gms.wallet.WalletConstants

/**
 * Everything the Google Pay API needs to describe what this app accepts. Field shape adapted from
 * `khalid64927/google-apple-pay`'s `GooglePayConfig` (Apache-2.0) — that repo isn't published to
 * Maven Central, so it can't be depended on directly; this is our own implementation using its config
 * shape as a starting point.
 *
 * `paymentsEnvironment = ENVIRONMENT_TEST` needs no live merchant account or business approval —
 * Google's own quickstart is explicit that TEST mode works out of the box for development, which is
 * why this gateway ships `SANDBOX_READY` rather than `MOCK_MODE`.
 */
data class GooglePayConfig(
    /** The payment processor this token will be handed to — "example" is Google's TEST-mode placeholder. */
    val gateway: String = "example",
    val gatewayMerchantId: String = "exampleGatewayMerchantId",
    // ponytail: generic display name — R17, no reference-app branding in the public toolkit.
    val merchantName: String = "kmp-toolkit Demo",
    val countryCode: String = "IN",
    val currencyCode: String = "INR",
    val allowedCardNetworks: List<String> = listOf("VISA", "MASTERCARD", "AMEX", "DISCOVER"),
    val allowedAuthMethods: List<String> = listOf("PAN_ONLY", "CRYPTOGRAM_3DS"),
    val paymentsEnvironment: Int = WalletConstants.ENVIRONMENT_TEST,
)
