package com.siddharth.kmp.paymentsapi

/**
 * Why a device-wallet button may or may not be drawn.
 *
 * Deliberately four states and not a `Boolean`. A Boolean collapses three completely different
 * situations — the merchant id was never provisioned, the hardware/OS cannot do wallets at all, and
 * the user simply has no card in their wallet yet — into one "false" that the UI can only answer by
 * hiding the button silently. Each of these wants a different response: a build-time error, nothing
 * at all, and an "add a card" affordance respectively.
 *
 * [NOT_CONFIGURED] is the state that exists because of the placeholder contract: while a
 * `__PROVISION_*__` sentinel is still in place the seam reports it, so the app refuses to draw a
 * working-looking button that would fail at the exact moment money is on the line.
 */
enum class WalletAvailability {
    /** Wallet is usable right now: configured, supported, and at least one card is provisioned. */
    AVAILABLE,

    /** Device and config are fine, but the user has no card in the wallet. Offer "add a card". */
    NO_CARDS_PROVISIONED,

    /** This device/OS cannot present the wallet at all. Show nothing — there is no user action. */
    UNSUPPORTED_DEVICE,

    /** A provisioning value is still a sentinel. A developer error, never a user-facing state. */
    NOT_CONFIGURED,
}

/**
 * Card networks a wallet may be asked to accept.
 *
 * Kept as a plain enum with no wire names attached: Google Pay spells these as its own uppercase
 * `allowedCardNetworks` strings and Apple Pay as `PKPaymentNetwork` constants, and neither spelling
 * belongs in the shared contract. Each provider maps this enum at its own edge.
 */
enum class CardNetwork {
    VISA,
    MASTERCARD,
    AMEX,
    DISCOVER,
    JCB,
    MAESTRO,
    INTERAC,
}

/**
 * The merchant identity a wallet transaction is presented under.
 *
 * [merchantId] is the one field that cannot be invented: Apple Pay needs a `merchant.*` identifier
 * issued by the Apple Developer portal, Google Pay needs the PSP's gateway merchant id. Both ship as
 * `__PROVISION_*__` sentinels, which is what [isProvisioned] detects.
 */
data class WalletMerchantConfig(
    val merchantId: String,
    val merchantName: String,
    /** ISO-3166-1 alpha-2 country of the merchant, e.g. `IN`, `US`. */
    val countryCode: String,
    /** ISO-4217 currency the summary item is priced in, e.g. `INR`, `USD`. */
    val currencyCode: String,
    val supportedNetworks: Set<CardNetwork>,
) {
    /**
     * False while [merchantId] is still an unreplaced provisioning sentinel.
     *
     * Matched structurally (`__PROVISION_` + `__`) rather than against one known key, so a sentinel
     * this module has never heard of is still caught instead of being handed to a wallet SDK.
     */
    val isProvisioned: Boolean
        get() = !(merchantId.startsWith("__PROVISION_") && merchantId.endsWith("__"))
}

/**
 * The authorization material a wallet hands back, normalized across vendors.
 *
 * [paymentData] is an opaque, encrypted blob — base64 of Apple's `PKPaymentToken.paymentData`, or
 * Google's `paymentMethodData.tokenizationData.token` JSON. It is meaningless to the client and is
 * only ever forwarded to the PSP by the backend; nothing here may branch on its contents.
 */
data class WalletToken(
    val paymentData: String,
    /** The network the user actually paid with, when the wallet reports one. */
    val network: CardNetwork?,
    /** Safe-to-display card description, e.g. `Visa 1234`. Never a PAN. */
    val displayLabel: String?,
)

/**
 * A [PaymentGateway] backed by a device wallet (Apple Pay, Google Pay).
 *
 * Adds exactly one thing to the base contract — [availability] — because a wallet is the one payment
 * method whose button must not be drawn until the platform says it will work. Everything else (the
 * `prepare`/`pay` split, the host indirection) is unchanged, so a wallet provider drops into the
 * same registry as every card gateway.
 */
interface WalletGateway : PaymentGateway {
    val merchantConfig: WalletMerchantConfig

    /**
     * Ask the platform whether the wallet can be presented, *before* any UI is drawn.
     *
     * Suspending because the Google Pay answer is a Play Services round-trip. Implementations must
     * return [WalletAvailability.NOT_CONFIGURED] whenever [merchantConfig] is not
     * [WalletMerchantConfig.isProvisioned], without calling the vendor SDK at all.
     */
    suspend fun availability(host: PaymentHost): WalletAvailability
}
