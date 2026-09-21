package com.siddharth.kmp.provider.googlepay

import com.siddharth.kmp.paymentsapi.CardNetwork
import com.siddharth.kmp.paymentsapi.WalletMerchantConfig

/**
 * Which Google Pay backend the request is aimed at.
 *
 * A commonMain enum rather than `WalletConstants.ENVIRONMENT_*` directly: those are Play Services
 * ints, and letting one into the config is exactly what used to pin this whole file to androidMain.
 * Mapped at the Android edge by `toWalletConstant`.
 */
enum class GooglePayEnvironment {
    /** No live merchant account, no business approval, real sheet. Google's documented dev path. */
    TEST,
    PRODUCTION,
}

/**
 * Everything the Google Pay API needs to describe what this app accepts. Field shape adapted from
 * `khalid64927/google-apple-pay`'s `GooglePayConfig` (Apache-2.0) — that repo isn't published to
 * Maven Central, so it can't be depended on directly; this is our own implementation using its config
 * shape as a starting point.
 *
 * The defaults are a genuinely working TEST configuration, not sentinels — Google's own quickstart
 * is explicit that `gateway = "example"` in `ENVIRONMENT_TEST` works out of the box. That is the
 * whole reason this gateway ships `SANDBOX_READY` while `:provider:applepay`, which cannot be run
 * at all without a paid Apple merchant id, ships `KYC_GATED`. Production values are registered in
 * `provisioning/placeholders.json` as `GOOGLE_PAY_GATEWAY` / `GOOGLE_PAY_GATEWAY_MERCHANT_ID`.
 */
data class GooglePayConfig(
    /** The payment processor this token will be handed to — "example" is Google's TEST-mode placeholder. */
    val gateway: String = "example",
    val gatewayMerchantId: String = "exampleGatewayMerchantId",
    // ponytail: generic display name — R17, no reference-app branding in the public toolkit.
    val merchantName: String = "kmp-toolkit Demo",
    val countryCode: String = "IN",
    val currencyCode: String = "INR",
    val allowedCardNetworks: Set<CardNetwork> =
        setOf(CardNetwork.VISA, CardNetwork.MASTERCARD, CardNetwork.AMEX, CardNetwork.DISCOVER),
    val allowedAuthMethods: List<String> = listOf("PAN_ONLY", "CRYPTOGRAM_3DS"),
    val environment: GooglePayEnvironment = GooglePayEnvironment.TEST,
) {
    /** This config projected onto the shared wallet contract. */
    val merchantConfig: WalletMerchantConfig =
        WalletMerchantConfig(
            merchantId = gatewayMerchantId,
            merchantName = merchantName,
            countryCode = countryCode,
            currencyCode = currencyCode,
            supportedNetworks = allowedCardNetworks,
        )

    /**
     * False only while a `__PROVISION_*__` sentinel is still in place. Unlike Apple Pay there is no
     * format check to add: "example" is a real, working value here, so anything non-sentinel is
     * something the developer chose.
     */
    val isProvisioned: Boolean
        get() = merchantConfig.isProvisioned && allowedCardNetworks.isNotEmpty()
}
