package com.siddharth.kmp.provider.googlepay

import com.siddharth.kmp.common.minorToDecimalString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds the Google Pay API's `IsReadyToPayRequest`/`PaymentDataRequest` JSON. This shape is
 * Google's own standard sample request (also mirrored by `khalid64927/google-apple-pay`,
 * Apache-2.0) — there's no meaningful way to build a "different" version of this JSON, it's the
 * documented contract every Android Google Pay integration sends.
 *
 * Built with kotlinx.serialization rather than `org.json`. Not a style preference: `org.json` is an
 * Android system class stubbed to throw on the JVM, which is why the tests for this file used to
 * need Robolectric to assert on a string. The Play Services API takes the JSON as text
 * (`PaymentDataRequest.fromJson`), so it never sees which library produced it.
 *
 * `docs: https://developers.google.com/pay/api/android/reference/request-objects`
 */
class GooglePayRequestBuilder(
    private val config: GooglePayConfig,
) {
    private fun tokenizationSpecification(): JsonObject =
        buildJsonObject {
            put("type", "PAYMENT_GATEWAY")
            put(
                "parameters",
                buildJsonObject {
                    put("gateway", config.gateway)
                    put("gatewayMerchantId", config.gatewayMerchantId)
                },
            )
        }

    private fun baseCardPaymentMethod(withTokenization: Boolean): JsonObject =
        buildJsonObject {
            put("type", "CARD")
            put(
                "parameters",
                buildJsonObject {
                    put("allowedAuthMethods", buildJsonArray { config.allowedAuthMethods.forEach { add(JsonPrimitive(it)) } })
                    // CardNetwork's names are Google's own `allowedCardNetworks` spellings, so this
                    // is a rename-safe mapping rather than a parallel list of magic strings.
                    put("allowedCardNetworks", buildJsonArray { config.allowedCardNetworks.forEach { add(JsonPrimitive(it.name)) } })
                    put("billingAddressRequired", false)
                },
            )
            if (withTokenization) put("tokenizationSpecification", tokenizationSpecification())
        }

    /** Whether the device even has a usable card on file — call before showing a Google Pay button. */
    fun isReadyToPayRequest(): JsonObject =
        buildJsonObject {
            putApiVersion()
            // Deliberately WITHOUT the tokenization spec: isReadyToPay asks about the device, and
            // sending merchant credentials in that probe leaks them into a call that does not need them.
            put("allowedPaymentMethods", buildJsonArray { add(baseCardPaymentMethod(withTokenization = false)) })
        }

    /** The full checkout request — amount, currency, merchant info, tokenization spec. */
    fun paymentDataRequest(amountMinor: Long): JsonObject =
        buildJsonObject {
            putApiVersion()
            put("allowedPaymentMethods", buildJsonArray { add(baseCardPaymentMethod(withTokenization = true)) })
            put(
                "transactionInfo",
                buildJsonObject {
                    put("totalPrice", amountMinor.minorToDecimalString())
                    put("totalPriceStatus", "FINAL")
                    put("countryCode", config.countryCode)
                    put("currencyCode", config.currencyCode)
                },
            )
            put("merchantInfo", buildJsonObject { put("merchantName", config.merchantName) })
        }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putApiVersion() {
    put("apiVersion", 2)
    put("apiVersionMinor", 0)
}
