package com.siddharth.kmp.provider.googlepay

import com.siddharth.kmp.common.minorToDecimalString
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the Google Pay API's `IsReadyToPayRequest`/`PaymentDataRequest` JSON. This shape is
 * Google's own standard sample request (also mirrored by `khalid64927/google-apple-pay`,
 * Apache-2.0) — there's no meaningful way to build a "different" version of this JSON, it's the
 * documented contract every Android Google Pay integration sends.
 *
 * `docs: https://developers.google.com/pay/api/android/reference/request-objects`
 */
class GooglePayRequestBuilder(
    private val config: GooglePayConfig,
) {
    private val baseRequest =
        JSONObject().apply {
            put("apiVersion", 2)
            put("apiVersionMinor", 0)
        }

    private fun tokenizationSpecification(): JSONObject =
        JSONObject().apply {
            put("type", "PAYMENT_GATEWAY")
            put(
                "parameters",
                JSONObject().apply {
                    put("gateway", config.gateway)
                    put("gatewayMerchantId", config.gatewayMerchantId)
                },
            )
        }

    private fun baseCardPaymentMethod(): JSONObject =
        JSONObject().apply {
            put("type", "CARD")
            put(
                "parameters",
                JSONObject().apply {
                    put("allowedAuthMethods", JSONArray(config.allowedAuthMethods))
                    put("allowedCardNetworks", JSONArray(config.allowedCardNetworks))
                    put("billingAddressRequired", false)
                },
            )
        }

    private fun cardPaymentMethod(): JSONObject =
        baseCardPaymentMethod().put("tokenizationSpecification", tokenizationSpecification())

    /** Whether the device even has a usable card on file — call before showing a Google Pay button. */
    fun isReadyToPayRequest(): JSONObject =
        JSONObject(baseRequest.toString()).apply {
            put("allowedPaymentMethods", JSONArray().put(baseCardPaymentMethod()))
        }

    /** The full checkout request — amount, currency, merchant info, tokenization spec. */
    fun paymentDataRequest(amountMinor: Long): JSONObject =
        JSONObject(baseRequest.toString()).apply {
            put("allowedPaymentMethods", JSONArray().put(cardPaymentMethod()))
            put(
                "transactionInfo",
                JSONObject().apply {
                    put("totalPrice", amountMinor.minorToDecimalString())
                    put("totalPriceStatus", "FINAL")
                    put("countryCode", config.countryCode)
                    put("currencyCode", config.currencyCode)
                },
            )
            put("merchantInfo", JSONObject().put("merchantName", config.merchantName))
        }
}
