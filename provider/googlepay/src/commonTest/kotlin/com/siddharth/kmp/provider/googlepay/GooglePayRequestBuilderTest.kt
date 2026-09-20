package com.siddharth.kmp.provider.googlepay

import com.siddharth.kmp.paymentsapi.CardNetwork
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * No device/emulator was available to exercise `GooglePayGateway` against a real Google Pay sheet —
 * this test covers the one part of the integration that's pure logic and genuinely verifiable off a
 * device: the request JSON this app sends actually matches Google's documented shape.
 *
 * Lives in commonTest and needs no Robolectric since the builder moved off `org.json`; the previous
 * version of this file existed on Robolectric solely because `org.json.JSONObject` is a throwing
 * stub on a plain JVM.
 */
class GooglePayRequestBuilderTest {
    private val config =
        GooglePayConfig(
            gateway = "example",
            gatewayMerchantId = "exampleGatewayMerchantId",
            merchantName = "kmp-toolkit Demo",
            countryCode = "IN",
            currencyCode = "INR",
        )
    private val builder = GooglePayRequestBuilder(config)

    private fun JsonObject.methods(): JsonArray = getValue("allowedPaymentMethods").jsonArray

    private fun JsonObject.firstMethod(): JsonObject = methods()[0].jsonObject

    @Test
    fun isReadyToPayRequestDeclaresCardAsTheOnlyPaymentMethod() {
        val json = builder.isReadyToPayRequest()

        assertEquals(2, json.getValue("apiVersion").jsonPrimitive.content.toInt())
        assertEquals(1, json.methods().size)
        assertEquals("CARD", json.firstMethod().getValue("type").jsonPrimitive.content)
    }

    @Test
    fun isReadyToPayRequestDoesNotLeakMerchantCredentials() {
        // The device probe has no business carrying the gateway merchant id.
        assertNull(builder.isReadyToPayRequest().firstMethod()["tokenizationSpecification"])
    }

    @Test
    fun paymentDataRequestCarriesTheTokenizationGatewayAndMerchantId() {
        val parameters =
            builder
                .paymentDataRequest(amountMinor = 14_900L)
                .firstMethod()
                .getValue("tokenizationSpecification")
                .jsonObject

        assertEquals("PAYMENT_GATEWAY", parameters.getValue("type").jsonPrimitive.content)
        val credentials = parameters.getValue("parameters").jsonObject
        assertEquals("example", credentials.getValue("gateway").jsonPrimitive.content)
        assertEquals("exampleGatewayMerchantId", credentials.getValue("gatewayMerchantId").jsonPrimitive.content)
    }

    @Test
    fun paymentDataRequestConvertsMinorUnitsToAMajorUnitDecimalString() {
        val transactionInfo =
            builder.paymentDataRequest(amountMinor = 14_900L).getValue("transactionInfo").jsonObject

        assertEquals("149.00", transactionInfo.getValue("totalPrice").jsonPrimitive.content)
        assertEquals("INR", transactionInfo.getValue("currencyCode").jsonPrimitive.content)
        assertEquals("FINAL", transactionInfo.getValue("totalPriceStatus").jsonPrimitive.content)
    }

    @Test
    fun merchantNameFlowsThroughToMerchantInfo() {
        val json = builder.paymentDataRequest(amountMinor = 100L)

        val merchantInfo = json.getValue("merchantInfo").jsonObject
        assertEquals("kmp-toolkit Demo", merchantInfo.getValue("merchantName").jsonPrimitive.content)
    }

    @Test
    fun allowedCardNetworksAndAuthMethodsAreConfigurable() {
        val customConfig =
            config.copy(allowedCardNetworks = setOf(CardNetwork.VISA), allowedAuthMethods = listOf("PAN_ONLY"))
        val parameters =
            GooglePayRequestBuilder(customConfig).isReadyToPayRequest().firstMethod().getValue("parameters").jsonObject

        val networks = parameters.getValue("allowedCardNetworks").jsonArray
        assertEquals(1, networks.size)
        // CardNetwork.VISA must serialize as Google's own wire spelling, not as "Visa".
        assertEquals("VISA", networks[0].jsonPrimitive.content)
        assertEquals("PAN_ONLY", parameters.getValue("allowedAuthMethods").jsonArray[0].jsonPrimitive.content)
    }
}
