package com.siddharth.kmp.provider.applepay

import com.siddharth.kmp.paymentsapi.CardNetwork
import com.siddharth.kmp.paymentsapi.WalletMerchantConfig

/**
 * The merchant identifier Apple issues, as a provisioning sentinel until somebody replaces it.
 *
 * Registered in `provisioning/placeholders.json` with its real format (`^merchant\.[...]$`) and its
 * provenance (Apple Developer portal > Identifiers > Merchant IDs, which needs a $99/yr Apple
 * Developer Program membership). Swapped by `provisioning/provision.sh apply`.
 */
const val APPLE_MERCHANT_ID_SENTINEL: String = "__PROVISION_APPLE_MERCHANT_ID__"

/**
 * Everything `PKPaymentRequest` needs, in commonMain so it is constructible and testable without
 * PassKit.
 *
 * Ships unprovisioned on purpose. [isProvisioned] is the gate the gateway and the button both read,
 * and it is false out of the box — the module is useless until a real merchant id is swapped in, and
 * it says so through the seam rather than by throwing at authorization time.
 */
data class ApplePayConfig(
    val merchantId: String = APPLE_MERCHANT_ID_SENTINEL,
    // ponytail: generic display name — no reference-app branding in the public toolkit.
    val merchantName: String = "kmp-toolkit Demo",
    val countryCode: String = "IN",
    val currencyCode: String = "INR",
    val supportedNetworks: Set<CardNetwork> =
        setOf(CardNetwork.VISA, CardNetwork.MASTERCARD, CardNetwork.AMEX, CardNetwork.DISCOVER),
) {
    /** This config projected onto the shared wallet contract. */
    val merchantConfig: WalletMerchantConfig =
        WalletMerchantConfig(
            merchantId = merchantId,
            merchantName = merchantName,
            countryCode = countryCode,
            currencyCode = currencyCode,
            supportedNetworks = supportedNetworks,
        )

    /**
     * True only when a real Apple merchant identifier is in place.
     *
     * Two conditions, not one. The sentinel check catches "nobody ran provision.sh"; the
     * `merchant.` prefix catches the worse case — somebody swapped in a *wrong* value, e.g. the app
     * bundle id. PassKit's answer to a malformed merchant id is a sheet that presents and then
     * fails to authorize, which is the failure mode this whole contract exists to prevent.
     */
    val isProvisioned: Boolean
        get() = merchantConfig.isProvisioned && merchantId.startsWith("merchant.")
}
