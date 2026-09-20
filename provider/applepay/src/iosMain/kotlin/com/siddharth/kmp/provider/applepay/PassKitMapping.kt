@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.siddharth.kmp.provider.applepay

import com.siddharth.kmp.paymentsapi.CardNetwork
import com.siddharth.kmp.paymentsapi.WalletToken
import platform.Foundation.base64EncodedStringWithOptions
import platform.PassKit.PKPayment
import platform.PassKit.PKPaymentNetwork
import platform.PassKit.PKPaymentNetworkAmex
import platform.PassKit.PKPaymentNetworkDiscover
import platform.PassKit.PKPaymentNetworkInterac
import platform.PassKit.PKPaymentNetworkJCB
import platform.PassKit.PKPaymentNetworkMaestro
import platform.PassKit.PKPaymentNetworkMasterCard
import platform.PassKit.PKPaymentNetworkVisa

/**
 * Shared enum → `PKPaymentNetwork` constant.
 *
 * Returns null rather than guessing a string. `PKPaymentNetwork` is a typed string constant, and an
 * invented one is not rejected — it is silently ignored by `canMakePaymentsUsingNetworks`, so a typo
 * shows up as "the user has no cards" rather than as an error.
 */
internal fun toPKPaymentNetwork(network: CardNetwork): PKPaymentNetwork? =
    when (network) {
        CardNetwork.VISA -> PKPaymentNetworkVisa
        CardNetwork.MASTERCARD -> PKPaymentNetworkMasterCard
        CardNetwork.AMEX -> PKPaymentNetworkAmex
        CardNetwork.DISCOVER -> PKPaymentNetworkDiscover
        CardNetwork.JCB -> PKPaymentNetworkJCB
        CardNetwork.MAESTRO -> PKPaymentNetworkMaestro
        CardNetwork.INTERAC -> PKPaymentNetworkInterac
    }

/** `PKPaymentNetwork` constant → shared enum. Null for any network this toolkit has no name for. */
internal fun fromPKPaymentNetwork(network: PKPaymentNetwork?): CardNetwork? =
    when (network) {
        null -> null
        PKPaymentNetworkVisa -> CardNetwork.VISA
        PKPaymentNetworkMasterCard -> CardNetwork.MASTERCARD
        PKPaymentNetworkAmex -> CardNetwork.AMEX
        PKPaymentNetworkDiscover -> CardNetwork.DISCOVER
        PKPaymentNetworkJCB -> CardNetwork.JCB
        PKPaymentNetworkMaestro -> CardNetwork.MAESTRO
        PKPaymentNetworkInterac -> CardNetwork.INTERAC
        else -> null
    }

/**
 * Normalizes `PKPayment` onto the shared [WalletToken].
 *
 * `paymentData` is base64 of an opaque encrypted blob that only the PSP holding the matching
 * payment-processing certificate can open. Nothing on the client may branch on it; it exists to be
 * forwarded. `displayName` ("Visa 1234") is the suffix Apple already considers safe to show — it is
 * not a PAN and must not be treated as one.
 */
internal fun PKPayment.toWalletToken(): WalletToken =
    WalletToken(
        paymentData = token.paymentData.base64EncodedStringWithOptions(0uL),
        network = fromPKPaymentNetwork(token.paymentMethod.network),
        displayLabel = token.paymentMethod.displayName,
    )
